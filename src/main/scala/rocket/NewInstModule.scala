// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.rocket

import Chisel._
import Chisel.ImplicitConversions._
import freechips.rocketchip.util._
import ALU._

//Data requested
class NewInstReq(dataBits: Int, tagBits: Int) extends Bundle {
  val fn = Bits(width = SZ_ALU_FN)
  val dw = Bits(width = SZ_DW)
  val in1 = Bits(width = dataBits)
  val in2 = Bits(width = dataBits)
  val tag = UInt(width = tagBits)
  override def cloneType = new NewInstReq(dataBits, tagBits).asInstanceOf[this.type]
}

//Data responded
class NewInstResp(dataBits: Int, tagBits: Int) extends Bundle {
  val data = Bits(width = dataBits)
  val tag = UInt(width = tagBits)
  override def cloneType = new NewInstResp(dataBits, tagBits).asInstanceOf[this.type]
}

//IO wrapper
class NewInstIO(dataBits: Int, tagBits: Int) extends Bundle {
  val req = Decoupled(new NewInstReq(dataBits, tagBits)).flip
  val kill = Bool(INPUT)
  val resp = Decoupled(new NewInstResp(dataBits, tagBits))
}

/*
Decoupled adds:
(in) .ready ready Bool
(out) .valid valid Bool
(out) .bits data
*/

case class NewInstParams(
  /* 
  Default values?
  mulUnroll: Int = 1,
  divUnroll: Int = 1,
  mulEarlyOut: Boolean = false,
  divEarlyOut: Boolean = false
  */

  //TODO?
)

/*
  MAJOR QUESTIONS IN LIFE:
    1. WHAT THE HELL ARE THE TAGBITS FOR?
    2. WHAT THE HELL DOES nXpr MEAN?
    3. IS ALU IMPORTED ONLY FOR THE ALU_FN?
*/

/*
  Some Answers:
    1. x.fire() ===  x.ready && x.valid
*/

class NewInst(/*cfg: NewInstParams,*/ width: Int, nXpr: Int = 32) extends Module {
  val io = new NewInstIO(width, log2Up(nXpr))
  val w = io.req.bits.in1.getWidth

  //val mulw = (w + cfg.mulUnroll - 1) / cfg.mulUnroll * cfg.mulUnroll
  //val fastMulW = w/2 > cfg.mulUnroll && w % (2*cfg.mulUnroll) == 0
 
  //val s_ready :: s_neg_inputs :: s_mul :: s_div :: s_dummy :: s_neg_output :: s_done_mul :: s_done_div :: Nil = Enum(UInt(), 8) //FOR STATE MACHINE
  
  val s_ready :: s_add :: s_done :: Nil = Enum(UInt(), 3)
  val state = Reg(init=s_ready)

  val req = Reg(io.req.bits)
  val count = Reg(UInt(width=w))
  val holder = Reg(UInt(width=w))


  when (state == s_add) {
    count := count - 1
    holder := holder + 1

    when (count === 0) {
      state := s_done
    }
  }  
  when (io.resp.fire() || io.kill) {
    state := s_ready //Important, used to kill/end/finish the execution
  }
  when (io.req.fire()) { //Seems to set the input?
    //This is kind of sneaky, it deviates from state === ready, and therefore no fire
    state := s_add
    //isHi := cmdHi
    //resHi := false
    count := io.req.bits.in2
    holder := io.req.bits.in1
    //neg_out := Mux(cmdHi, lhs_sign, lhs_sign =/= rhs_sign)
    //divisor := Cat(rhs_sign, rhs_in)
    //remainder := lhs_in
    req := io.req.bits
  }

  //Seems like the output but is hard to decipher
  //Because of being decoupled?

  //val outMul = (state & (s_done_mul ^ s_done_div)) === (s_done_mul & ~s_done_div)
  //val loOut = Mux(Bool(fastMulW) && halfWidth(req) && outMul, result(w-1,w/2), result(w/2-1,0))
  //val hiOut = Mux(halfWidth(req), Fill(w/2, loOut(w/2-1)), result(w-1,w/2))

  //This is the actual output
  io.resp.bits <> req
  io.resp.bits.data := holder
  io.resp.valid := (state === s_done)
  io.req.ready := state === s_ready
}
