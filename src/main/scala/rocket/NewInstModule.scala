// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.util._
import ALU._

class NewInstReq(dataBits: Int, tagBits: Int) extends Bundle {
  val fn = Bits(width = SZ_ALU_FN)
  val dw = Bits(width = SZ_DW)
  val in1 = Bits(width = dataBits)
  val in2 = Bits(width = dataBits)
  val tag = UInt(width = tagBits)
  override def cloneType = new NewInstReq(dataBits, tagBits).asInstanceOf[this.type]
}

class NewInstResp(dataBits: Int, tagBits: Int) extends Bundle {
  val data = Bits(width = dataBits)
  val tag = UInt(width = tagBits)
  override def cloneType = new NewInstResp(dataBits, tagBits).asInstanceOf[this.type]
}

class NewInstIO(dataBits: Int, tagBits: Int) extends Bundle {
  val req = Decoupled(new NewInstReq(dataBits, tagBits)).flip
  val kill = Bool(INPUT)
  val resp = Decoupled(new NewInstResp(dataBits, tagBits))
}

case class NewInstParams(
  mulUnroll: Int = 1,
  divUnroll: Int = 1,
  mulEarlyOut: Boolean = false,
  divEarlyOut: Boolean = false
)

class NewInst(cfg: MulDivParams, width: Int, nXpr: Int = 32) extends Module {
  val io = new NewInstIO(width, log2Up(nXpr))
  val w = io.req.bits.in1.getWidth
  val mulw = (w + cfg.mulUnroll - 1) / cfg.mulUnroll * cfg.mulUnroll
  val fastMulW = w/2 > cfg.mulUnroll && w % (2*cfg.mulUnroll) == 0
 
  val s_ready :: s_neg_inputs :: s_mul :: s_div :: s_dummy :: s_neg_output :: s_done_mul :: s_done_div :: Nil = Enum(UInt(), 8)
  val state = Reg(init=s_ready)
 
  val req = Reg(io.req.bits)
  val count = Reg(UInt(width = log2Ceil((w/cfg.divUnroll + 1) max (w/cfg.mulUnroll))))
  val neg_out = Reg(Bool())
  val isHi = Reg(Bool())
  val resHi = Reg(Bool())
  val divisor = Reg(Bits(width = w+1)) // div only needs w bits
  val remainder = Reg(Bits(width = 2*mulw+2)) // div only needs 2*w+1 bits

  val cmdMul :: cmdHi :: lhsSigned :: rhsSigned :: Nil =
    DecodeLogic(io.req.bits.fn, List(X, X, X, X), List(
                   FN_DIV    -> List(N, N, Y, Y),
                   FN_REM    -> List(N, Y, Y, Y),
                   FN_DIVU   -> List(N, N, N, N),
                   FN_REMU   -> List(N, Y, N, N),
                   FN_NINST  -> List(Y, N, X, X),
                   // FN_MUL    -> List(Y, N, X, X),
                   FN_MULH   -> List(Y, Y, Y, Y),
                   FN_MULHU  -> List(Y, Y, N, N),
                   FN_MULHSU -> List(Y, Y, Y, N))).map(_ toBool)

  require(w == 32 || w == 64)
  def halfWidth(req: NewInstReq) = Bool(w > 32) && req.dw === DW_32

  def sext(x: Bits, halfW: Bool, signed: Bool) = {
    val sign = signed && Mux(halfW, x(w/2-1), x(w-1))
    val hi = Mux(halfW, Fill(w/2, sign), x(w-1,w/2))
    (Cat(hi, x(w/2-1,0)), sign)
  }
  val (lhs_in, lhs_sign) = sext(io.req.bits.in1, halfWidth(io.req.bits), lhsSigned)
  val (rhs_in, rhs_sign) = sext(io.req.bits.in2, halfWidth(io.req.bits), rhsSigned)
  
  val subtractor = remainder(2*w,w) - divisor
  val result = Mux(resHi, remainder(2*w, w+1), remainder(w-1, 0))
  val negated_remainder = -result

  when (state === s_neg_inputs) {
    when (remainder(w-1)) {
      remainder := negated_remainder
    }
    when (divisor(w-1)) {
      divisor := subtractor
    }
    state := s_div
  }
  when (state === s_neg_output) {
    remainder := negated_remainder
    state := s_done_div
    resHi := false
  }
  when (state === s_mul) {
    val mulReg = Cat(remainder(2*mulw+1,w+1),remainder(w-1,0))
    val mplierSign = remainder(w)
    val mplier = mulReg(mulw-1,0)
    val accum = mulReg(2*mulw,mulw).asSInt
    val mpcand = divisor.asSInt
    val prod = Cat(mplierSign, mplier(cfg.mulUnroll-1, 0)).asSInt * mpcand + accum
    val nextMulReg = Cat(prod, mplier(mulw-1, cfg.mulUnroll))
    val nextMplierSign = count === mulw/cfg.mulUnroll-2 && neg_out

    val eOutMask = (SInt(BigInt(-1) << mulw) >> (count * cfg.mulUnroll)(log2Up(mulw)-1,0))(mulw-1,0)
    val eOut = Bool(cfg.mulEarlyOut) && count =/= mulw/cfg.mulUnroll-1 && count =/= 0 &&
      !isHi && (mplier & ~eOutMask) === UInt(0)
    val eOutRes = (mulReg >> (mulw - count * cfg.mulUnroll)(log2Up(mulw)-1,0))
    val nextMulReg1 = Cat(nextMulReg(2*mulw,mulw), Mux(eOut, eOutRes, nextMulReg)(mulw-1,0))
    remainder := Cat(nextMulReg1 >> w, nextMplierSign, nextMulReg1(w-1,0))

    count := count + 1
    when (eOut || count === mulw/cfg.mulUnroll-1) {
      state := s_done_mul
      resHi := isHi
    }
  }
  when (state === s_div) {
    val unrolls = ((0 until cfg.divUnroll) scanLeft remainder) { case (rem, i) =>
      // the special case for iteration 0 is to save HW, not for correctness
      val difference = if (i == 0) subtractor else rem(2*w,w) - divisor(w-1,0)
      val less = difference(w)
      Cat(Mux(less, rem(2*w-1,w), difference(w-1,0)), rem(w-1,0), !less)
    } tail

    remainder := unrolls.last
    when (count === w/cfg.divUnroll) {
      state := Mux(neg_out, s_neg_output, s_done_div)
      resHi := isHi
      if (w % cfg.divUnroll < cfg.divUnroll - 1)
        remainder := unrolls(w % cfg.divUnroll)
    }
    count := count + 1

    val divby0 = count === 0 && !subtractor(w)
    if (cfg.divEarlyOut) {
      val divisorMSB = Log2(divisor(w-1,0), w)
      val dividendMSB = Log2(remainder(w-1,0), w)
      val eOutPos = UInt(w-1) + divisorMSB - dividendMSB
      val eOutZero = divisorMSB > dividendMSB
      val eOut = count === 0 && !divby0 && (eOutPos >= cfg.divUnroll || eOutZero)
      when (eOut) {
        val inc = Mux(eOutZero, UInt(w-1), eOutPos) >> log2Floor(cfg.divUnroll)
        val shift = inc << log2Floor(cfg.divUnroll)
        remainder := remainder(w-1,0) << shift
        count := inc
      }
    }
    when (divby0 && !isHi) { neg_out := false }
  }
  when (io.resp.fire() || io.kill) {
    state := s_ready
  }
  when (io.req.fire()) {
    state := Mux(cmdMul, s_mul, Mux(lhs_sign || rhs_sign, s_neg_inputs, s_div))
    isHi := cmdHi
    resHi := false
    count := Mux[UInt](Bool(fastMulW) && cmdMul && halfWidth(io.req.bits), w/cfg.mulUnroll/2, 0)
    neg_out := Mux(cmdHi, lhs_sign, lhs_sign =/= rhs_sign)
    divisor := Cat(rhs_sign, rhs_in)
    remainder := lhs_in
    req := io.req.bits
  }

  val outMul = (state & (s_done_mul ^ s_done_div)) === (s_done_mul & ~s_done_div)
  val loOut = Mux(Bool(fastMulW) && halfWidth(req) && outMul, result(w-1,w/2), result(w/2-1,0))
  val hiOut = Mux(halfWidth(req), Fill(w/2, loOut(w/2-1)), result(w-1,w/2))
  io.resp.bits <> req
  io.resp.bits.data := Cat(hiOut, loOut)
  io.resp.valid := (state === s_done_mul || state === s_done_div)
  io.req.ready := state === s_ready
}
