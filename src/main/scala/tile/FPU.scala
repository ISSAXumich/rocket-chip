// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import Chisel._
import Chisel.ImplicitConversions._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket._
import freechips.rocketchip.rocket.Instructions._
import freechips.rocketchip.util._
import scala.util.Random

case class FPUParams(
  divSqrt: Boolean = true,
  sfdistLatency: Int = 30,
  sfmaLatency: Int = 3,
  dfmaLatency: Int = 4
)

object FPConstants
{
  val RM_SZ = 3
  val FLAGS_SZ = 5
}
import FPConstants._

trait HasFPUCtrlSigs {
  val ldst = Bool()
  val wen = Bool()
  val ren1 = Bool()
  val ren2 = Bool()
  val ren3 = Bool()
  val swap12 = Bool()
  val swap23 = Bool()
  val singleIn = Bool()
  val singleOut = Bool()
  val fromint = Bool()
  val toint = Bool()
  val fastpipe = Bool()
  val fma = Bool()
  val div = Bool()
  val sqrt = Bool()
  val wflags = Bool()
  val fdist = Bool()
}

class FPUCtrlSigs extends Bundle with HasFPUCtrlSigs

class FPUDecoder(implicit p: Parameters) extends FPUModule()(p) {
  val io = new Bundle {
    val inst = Bits(INPUT, 32)
    val sigs = new FPUCtrlSigs().asOutput
  }

  
  //                               singleOut
  //                              singleIn | fromint
  //                              swap23 | | | toint
  //                            swap12 | | | | | fastpipe
  //                            ren3 | | | | | | | fma
  //                          ren2 | | | | | | | | | div
  //                        ren1 | | | | | | | | | | | sqrt
  //                       wen | | | | | | | | | | | | | wflags
  //                    ldst | | | | | | | | | | | | | | | fdist
  //                       | | | | | | | | | | | | | | | | |
  val default =       List(X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X)
  val f =
    Array(FLW      -> List(Y,Y,N,N,N,X,X,X,X,N,N,N,N,N,N,N,N),
          FSW      -> List(Y,N,N,Y,N,Y,X,N,Y,N,Y,N,N,N,N,N,N),
          FMV_S_X  -> List(N,Y,N,N,N,X,X,Y,N,Y,N,N,N,N,N,N,N),
          FCVT_S_W -> List(N,Y,N,N,N,X,X,Y,Y,Y,N,N,N,N,N,Y,N),
          FCVT_S_WU-> List(N,Y,N,N,N,X,X,Y,Y,Y,N,N,N,N,N,Y,N),
          FCVT_S_L -> List(N,Y,N,N,N,X,X,Y,Y,Y,N,N,N,N,N,Y,N),
          FCVT_S_LU-> List(N,Y,N,N,N,X,X,Y,Y,Y,N,N,N,N,N,Y,N),
          FMV_X_S  -> List(N,N,Y,N,N,N,X,N,Y,N,Y,N,N,N,N,N,N),
          FCLASS_S -> List(N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,N,N),
          FCVT_W_S -> List(N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,Y,N),
          FCVT_WU_S-> List(N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,Y,N),
          FCVT_L_S -> List(N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,Y,N),
          FCVT_LU_S-> List(N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,Y,N),
          FEQ_S    -> List(N,N,Y,Y,N,N,N,Y,Y,N,Y,N,N,N,N,Y,N),
          FLT_S    -> List(N,N,Y,Y,N,N,N,Y,Y,N,Y,N,N,N,N,Y,N),
          FLE_S    -> List(N,N,Y,Y,N,N,N,Y,Y,N,Y,N,N,N,N,Y,N),
          FSGNJ_S  -> List(N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,N,N),
          FSGNJN_S -> List(N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,N,N),
          FSGNJX_S -> List(N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,N,N),
          FMIN_S   -> List(N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,Y,N),
          FMAX_S   -> List(N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,Y,N),
          FADD_S   -> List(N,Y,Y,Y,N,N,Y,Y,Y,N,N,N,Y,N,N,Y,N),
          FSUB_S   -> List(N,Y,Y,Y,N,N,Y,Y,Y,N,N,N,Y,N,N,Y,N),
          FMUL_S   -> List(N,Y,Y,Y,N,N,N,Y,Y,N,N,N,Y,N,N,Y,N),
          FDIST_S  -> List(N,Y,Y,Y,N,N,N,Y,Y,N,N,N,N,N,N,Y,Y),
          FMADD_S  -> List(N,Y,Y,Y,Y,N,N,Y,Y,N,N,N,Y,N,N,Y,N),
          FMSUB_S  -> List(N,Y,Y,Y,Y,N,N,Y,Y,N,N,N,Y,N,N,Y,N),
          FNMADD_S -> List(N,Y,Y,Y,Y,N,N,Y,Y,N,N,N,Y,N,N,Y,N),
          FNMSUB_S -> List(N,Y,Y,Y,Y,N,N,Y,Y,N,N,N,Y,N,N,Y,N),
          FDIV_S   -> List(N,Y,Y,Y,N,N,N,Y,Y,N,N,N,N,Y,N,Y,N),
          FSQRT_S  -> List(N,Y,Y,N,N,N,X,Y,Y,N,N,N,N,N,Y,Y,N))
  val d =
    Array(FLD      -> List(Y,Y,N,N,N,X,X,X,N,N,N,N,N,N,N,N,N),
          FSD      -> List(Y,N,N,Y,N,Y,X,N,N,N,Y,N,N,N,N,N,N),
          FMV_D_X  -> List(N,Y,N,N,N,X,X,X,N,Y,N,N,N,N,N,N,N),
          FCVT_D_W -> List(N,Y,N,N,N,X,X,N,N,Y,N,N,N,N,N,Y,N),
          FCVT_D_WU-> List(N,Y,N,N,N,X,X,N,N,Y,N,N,N,N,N,Y,N),
          FCVT_D_L -> List(N,Y,N,N,N,X,X,N,N,Y,N,N,N,N,N,Y,N),
          FCVT_D_LU-> List(N,Y,N,N,N,X,X,N,N,Y,N,N,N,N,N,Y,N),
          FMV_X_D  -> List(N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,N,N),
          FCLASS_D -> List(N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,N,N),
          FCVT_W_D -> List(N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,Y,N),
          FCVT_WU_D-> List(N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,Y,N),
          FCVT_L_D -> List(N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,Y,N),
          FCVT_LU_D-> List(N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,Y,N),
          FCVT_S_D -> List(N,Y,Y,N,N,N,X,N,Y,N,N,Y,N,N,N,Y,N),
          FCVT_D_S -> List(N,Y,Y,N,N,N,X,Y,N,N,N,Y,N,N,N,Y,N),
          FEQ_D    -> List(N,N,Y,Y,N,N,N,N,N,N,Y,N,N,N,N,Y,N),
          FLT_D    -> List(N,N,Y,Y,N,N,N,N,N,N,Y,N,N,N,N,Y,N),
          FLE_D    -> List(N,N,Y,Y,N,N,N,N,N,N,Y,N,N,N,N,Y,N),
          FSGNJ_D  -> List(N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,N,N),
          FSGNJN_D -> List(N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,N,N),
          FSGNJX_D -> List(N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,N,N),
          FMIN_D   -> List(N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,Y,N),
          FMAX_D   -> List(N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,Y,N),
          FADD_D   -> List(N,Y,Y,Y,N,N,Y,N,N,N,N,N,Y,N,N,Y,N),
          FSUB_D   -> List(N,Y,Y,Y,N,N,Y,N,N,N,N,N,Y,N,N,Y,N),
          FMUL_D   -> List(N,Y,Y,Y,N,N,N,N,N,N,N,N,Y,N,N,Y,N),
          FMADD_D  -> List(N,Y,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,Y,N),
          FMSUB_D  -> List(N,Y,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,Y,N),
          FNMADD_D -> List(N,Y,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,Y,N),
          FNMSUB_D -> List(N,Y,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,Y,N),
          FDIV_D   -> List(N,Y,Y,Y,N,N,N,N,N,N,N,N,N,Y,N,Y,N),
          FSQRT_D  -> List(N,Y,Y,N,N,N,X,N,N,N,N,N,N,N,Y,Y,N))

  val insns = fLen match {
    case 32 => f
    case 64 => f ++ d
  }
  val decoder = DecodeLogic(io.inst, default, insns)
  val s = io.sigs
  val sigs = Seq(s.ldst, s.wen, s.ren1, s.ren2, s.ren3, s.swap12,
                 s.swap23, s.singleIn, s.singleOut, s.fromint, s.toint,
                 s.fastpipe, s.fma, s.div, s.sqrt, s.wflags, s.fdist)
  sigs zip decoder map {case(s,d) => s := d}
}

class FPUCoreIO(implicit p: Parameters) extends CoreBundle()(p) {
  val inst = Bits(INPUT, 32)
  val fromint_data = Bits(INPUT, xLen)

  val fcsr_rm = Bits(INPUT, FPConstants.RM_SZ)
  val fcsr_flags = Valid(Bits(width = FPConstants.FLAGS_SZ))

  val store_data = Bits(OUTPUT, fLen)
  val toint_data = Bits(OUTPUT, xLen)

  val dmem_resp_val = Bool(INPUT)
  val dmem_resp_type = Bits(INPUT, 3)
  val dmem_resp_tag = UInt(INPUT, 5)
  val dmem_resp_data = Bits(INPUT, fLen)

  val valid = Bool(INPUT)
  val fcsr_rdy = Bool(OUTPUT)
  val nack_mem = Bool(OUTPUT)
  val illegal_rm = Bool(OUTPUT)
  val killx = Bool(INPUT)
  val killm = Bool(INPUT)
  val dec = new FPUCtrlSigs().asOutput
  val sboard_set = Bool(OUTPUT)
  val sboard_clr = Bool(OUTPUT)
  val sboard_clra = UInt(OUTPUT, 5)
}

class FPUIO(implicit p: Parameters) extends FPUCoreIO ()(p) {
  val cp_req = Decoupled(new FPInput()).flip //cp doesn't pay attn to kill sigs
  val cp_resp = Decoupled(new FPResult())
}

class FPResult(implicit p: Parameters) extends CoreBundle()(p) {
  val data = Bits(width = fLen+1)
  val exc = Bits(width = FPConstants.FLAGS_SZ)
}

class FPInput(implicit p: Parameters) extends CoreBundle()(p) with HasFPUCtrlSigs {
  val rm = Bits(width = FPConstants.RM_SZ)
  val fmaCmd = Bits(width = 2)
  val typ = Bits(width = 2)
  val in1 = Bits(width = fLen+1)
  val in2 = Bits(width = fLen+1)
  val in3 = Bits(width = fLen+1)

  override def cloneType = new FPInput().asInstanceOf[this.type]
}

class FPDistInput(implicit p: Parameters) extends CoreBundle()(p) {
  //val rm = Bits(width = FPConstants.RM_SZ) //Does this need a rounding mode?
  val x1 = Bits(width = fLen+1)
  val x2 = Bits(width = fLen+1)

  val y1 = Bits(width = fLen+1)
  val y2 = Bits(width = fLen+1)

  val z1 = Bits(width = fLen+1)
  val z2 = Bits(width = fLen+1)

  //val dist = Bits(width = fLen+1)

  override def cloneType = new FPDistInput().asInstanceOf[this.type]
}

/*
class FPDistResult(implicit p: Parameters) extends CoreBundle()(p) {
  val dist = Bits(width = fLen+1)
}
*/

case class FType(exp: Int, sig: Int) {
  def ieeeWidth = exp + sig
  def recodedWidth = ieeeWidth + 1

  def qNaN = UInt((BigInt(7) << (exp + sig - 3)) + (BigInt(1) << (sig - 2)), exp + sig + 1)
  def isNaN(x: UInt) = x(sig + exp - 1, sig + exp - 3).andR
  def isSNaN(x: UInt) = isNaN(x) && !x(sig - 2)

  def classify(x: UInt) = {
    val sign = x(sig + exp)
    val code = x(exp + sig - 1, exp + sig - 3)
    val codeHi = code(2, 1)
    val isSpecial = codeHi === UInt(3)

    val isHighSubnormalIn = x(exp + sig - 3, sig - 1) < UInt(2)
    val isSubnormal = code === UInt(1) || codeHi === UInt(1) && isHighSubnormalIn
    val isNormal = codeHi === UInt(1) && !isHighSubnormalIn || codeHi === UInt(2)
    val isZero = code === UInt(0)
    val isInf = isSpecial && !code(0)
    val isNaN = code.andR
    val isSNaN = isNaN && !x(sig-2)
    val isQNaN = isNaN && x(sig-2)

    Cat(isQNaN, isSNaN, isInf && !sign, isNormal && !sign,
        isSubnormal && !sign, isZero && !sign, isZero && sign,
        isSubnormal && sign, isNormal && sign, isInf && sign)
  }

  // convert between formats, ignoring rounding, range, NaN
  def unsafeConvert(x: UInt, to: FType) = if (this == to) x else {
    val sign = x(sig + exp)
    val fractIn = x(sig - 2, 0)
    val expIn = x(sig + exp - 1, sig - 1)
    val fractOut = fractIn << to.sig >> sig
    val expOut = {
      val expCode = expIn(exp, exp - 2)
      val commonCase = (expIn + (1 << to.exp)) - (1 << exp)
      Mux(expCode === 0 || expCode >= 6, Cat(expCode, commonCase(to.exp - 3, 0)), commonCase(to.exp, 0))
    }
    Cat(sign, expOut, fractOut)
  }

  def recode(x: UInt) = hardfloat.recFNFromFN(exp, sig, x)
  def ieee(x: UInt) = hardfloat.fNFromRecFN(exp, sig, x)
}

object FType {
  val S = new FType(8, 24)
  val D = new FType(11, 53)

  val all = List(S, D)
}

trait HasFPUParameters {
  val fLen: Int
  def xLen: Int
  val minXLen = 32
  val nIntTypes = log2Ceil(xLen/minXLen) + 1
  val floatTypes = FType.all.filter(_.ieeeWidth <= fLen)
  val minType = floatTypes.head
  val maxType = floatTypes.last
  def prevType(t: FType) = floatTypes(typeTag(t) - 1)
  val maxExpWidth = maxType.exp
  val maxSigWidth = maxType.sig
  def typeTag(t: FType) = floatTypes.indexOf(t)

  private def isBox(x: UInt, t: FType): Bool = x(t.sig + t.exp, t.sig + t.exp - 4).andR

  private def box(x: UInt, xt: FType, y: UInt, yt: FType): UInt = {
    require(xt.ieeeWidth == 2 * yt.ieeeWidth)
    val swizzledNaN = Cat(
      x(xt.sig + xt.exp, xt.sig + xt.exp - 3),
      x(xt.sig - 2, yt.recodedWidth - 1).andR,
      x(xt.sig + xt.exp - 5, xt.sig),
      y(yt.recodedWidth - 2),
      x(xt.sig - 2, yt.recodedWidth - 1),
      y(yt.recodedWidth - 1),
      y(yt.recodedWidth - 3, 0))
    Mux(xt.isNaN(x), swizzledNaN, x)
  }

  // implement NaN unboxing for FU inputs
  def unbox(x: UInt, tag: UInt, exactType: Option[FType]): UInt = {
    val outType = exactType.getOrElse(maxType)
    def helper(x: UInt, t: FType): Seq[(Bool, UInt)] = {
      val prev =
        if (t == minType) {
          Seq()
        } else {
          val prevT = prevType(t)
          val unswizzled = Cat(
            x(prevT.sig + prevT.exp - 1),
            x(t.sig - 1),
            x(prevT.sig + prevT.exp - 2, 0))
          val prev = helper(unswizzled, prevT)
          val isbox = isBox(x, t)
          prev.map(p => (isbox && p._1, p._2))
        }
      prev :+ (true.B, t.unsafeConvert(x, outType))
    }

    val (oks, floats) = helper(x, maxType).unzip
    if (exactType.isEmpty || floatTypes.size == 1) {
      Mux(oks(tag), floats(tag), maxType.qNaN)
    } else {
      val t = exactType.get
      floats(typeTag(t)) | Mux(oks(typeTag(t)), 0.U, t.qNaN)
    }
  }

  // make sure that the redundant bits in the NaN-boxed encoding are consistent
  def consistent(x: UInt): Bool = {
    def helper(x: UInt, t: FType): Bool = if (typeTag(t) == 0) true.B else {
      val prevT = prevType(t)
      val unswizzled = Cat(
        x(prevT.sig + prevT.exp - 1),
        x(t.sig - 1),
        x(prevT.sig + prevT.exp - 2, 0))
      val prevOK = !isBox(x, t) || helper(unswizzled, prevT)
      val curOK = !t.isNaN(x) || x(t.sig + t.exp - 4) === x(t.sig - 2, prevT.recodedWidth - 1).andR
      prevOK && curOK
    }
    helper(x, maxType)
  }

  // generate a NaN box from an FU result
  def box(x: UInt, tag: UInt): UInt = {
    def helper(y: UInt, yt: FType): UInt = {
      if (yt == maxType) {
        y
      } else {
        val nt = floatTypes(typeTag(yt) + 1)
        val bigger = box(UInt((BigInt(1) << nt.recodedWidth)-1), nt, y, yt)
        bigger | UInt((BigInt(1) << maxType.recodedWidth) - (BigInt(1) << nt.recodedWidth))
      }
    }
    val opts = floatTypes.map(t => helper(x, t))
    opts(tag)
  }

  // zap bits that hardfloat thinks are don't-cares, but we do care about
  def sanitizeNaN(x: UInt, t: FType): UInt = {
    if (typeTag(t) == 0) {
      x
    } else {
      val maskedNaN = x & ~UInt((BigInt(1) << (t.sig-1)) | (BigInt(1) << (t.sig+t.exp-4)), t.recodedWidth)
      Mux(t.isNaN(x), maskedNaN, x)
    }
  }

  // implement NaN boxing and recoding for FL*/fmv.*.x
  def recode(x: UInt, tag: UInt): UInt = {
    def helper(x: UInt, t: FType): UInt = {
      if (typeTag(t) == 0) {
        t.recode(x)
      } else {
        val prevT = prevType(t)
        box(t.recode(x), t, helper(x, prevT), prevT)
      }
    }

    // fill MSBs of subword loads to emulate a wider load of a NaN-boxed value
    val boxes = floatTypes.map(t => UInt((BigInt(1) << maxType.ieeeWidth) - (BigInt(1) << t.ieeeWidth)))
    helper(boxes(tag) | x, maxType)
  }

  // implement NaN unboxing and un-recoding for FS*/fmv.x.*
  def ieee(x: UInt, t: FType = maxType): UInt = {
    if (typeTag(t) == 0) {
      t.ieee(x)
    } else {
      val unrecoded = t.ieee(x)
      val prevT = prevType(t)
      val prevRecoded = Cat(
        x(prevT.recodedWidth-2),
        x(t.sig-1),
        x(prevT.recodedWidth-3, 0))
      val prevUnrecoded = ieee(prevRecoded, prevT)
      Cat(unrecoded >> prevT.ieeeWidth, Mux(t.isNaN(x), prevUnrecoded, unrecoded(prevT.ieeeWidth-1, 0)))
    }
  }
}

abstract class FPUModule(implicit p: Parameters) extends CoreModule()(p) with HasFPUParameters

class FPToInt(implicit p: Parameters) extends FPUModule()(p) {
  class Output extends Bundle {
    val in = new FPInput
    val lt = Bool()
    val store = Bits(width = fLen)
    val toint = Bits(width = xLen)
    val exc = Bits(width = FPConstants.FLAGS_SZ)
    override def cloneType = new Output().asInstanceOf[this.type]
  }
  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val out = Valid(new Output)
  }

  val in = RegEnable(io.in.bits, io.in.valid)
  val valid = Reg(next=io.in.valid)

  val dcmp = Module(new hardfloat.CompareRecFN(maxExpWidth, maxSigWidth))
  dcmp.io.a := in.in1
  dcmp.io.b := in.in2
  dcmp.io.signaling := !in.rm(1)

  val tag = !in.singleOut // TODO typeTag
  val store = ieee(in.in1)
  val toint = Wire(init = store)
  val intType = Wire(init = tag)
  io.out.bits.store := ((0 until nIntTypes).map(i => Fill(1 << (nIntTypes - i - 1), store((minXLen << i) - 1, 0))): Seq[UInt])(tag)
  io.out.bits.toint := ((0 until nIntTypes).map(i => toint((minXLen << i) - 1, 0).sextTo(xLen)): Seq[UInt])(intType)
  io.out.bits.exc := Bits(0)

  when (in.rm(0)) {
    val classify_out = (floatTypes.map(t => t.classify(maxType.unsafeConvert(in.in1, t))): Seq[UInt])(tag)
    toint := classify_out | (store >> minXLen << minXLen)
    intType := 0
  }

  when (in.wflags) { // feq/flt/fle, fcvt
    toint := (~in.rm & Cat(dcmp.io.lt, dcmp.io.eq)).orR | (store >> minXLen << minXLen)
    io.out.bits.exc := dcmp.io.exceptionFlags
    intType := 0

    when (!in.ren2) { // fcvt
      val cvtType = in.typ.extract(log2Ceil(nIntTypes), 1)
      intType := cvtType

      val conv = Module(new hardfloat.RecFNToIN(maxExpWidth, maxSigWidth, xLen))
      conv.io.in := in.in1
      conv.io.roundingMode := in.rm
      conv.io.signedOut := ~in.typ(0)
      toint := conv.io.out
      io.out.bits.exc := Cat(conv.io.intExceptionFlags(2, 1).orR, UInt(0, 3), conv.io.intExceptionFlags(0))

      for (i <- 0 until nIntTypes-1) {
        val w = minXLen << i
        when (cvtType === i) {
          val narrow = Module(new hardfloat.RecFNToIN(maxExpWidth, maxSigWidth, w))
          narrow.io.in := in.in1
          narrow.io.roundingMode := in.rm
          narrow.io.signedOut := ~in.typ(0)

          val excSign = in.in1(maxExpWidth + maxSigWidth) && !maxType.isNaN(in.in1)
          val excOut = Cat(conv.io.signedOut === excSign, Fill(w-1, !excSign))
          val invalid = conv.io.intExceptionFlags(2) || narrow.io.intExceptionFlags(1)
          when (invalid) { toint := Cat(conv.io.out >> w, excOut) }
          io.out.bits.exc := Cat(invalid, UInt(0, 3), !invalid && conv.io.intExceptionFlags(0))
        }
      }
    }
  }

  io.out.valid := valid
  io.out.bits.lt := dcmp.io.lt || (dcmp.io.a.asSInt < 0.S && dcmp.io.b.asSInt >= 0.S)
  io.out.bits.in := in
}

class IntToFP(val latency: Int)(implicit p: Parameters) extends FPUModule()(p) {
  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val out = Valid(new FPResult)
  }

  val in = Pipe(io.in)
  val tag = !in.bits.singleIn // TODO typeTag

  val mux = Wire(new FPResult)
  mux.exc := Bits(0)
  mux.data := recode(in.bits.in1, !in.bits.singleIn)

  val intValue = {
    val res = Wire(init = in.bits.in1.asSInt)
    for (i <- 0 until nIntTypes-1) {
      val smallInt = in.bits.in1((minXLen << i) - 1, 0)
      when (in.bits.typ.extract(log2Ceil(nIntTypes), 1) === i) {
        res := Mux(in.bits.typ(0), smallInt.zext, smallInt.asSInt)
      }
    }
    res.asUInt
  }

  when (in.bits.wflags) { // fcvt
    // could be improved for RVD/RVQ with a single variable-position rounding
    // unit, rather than N fixed-position ones
    val i2fResults = for (t <- floatTypes) yield {
      val i2f = Module(new hardfloat.INToRecFN(xLen, t.exp, t.sig))
      i2f.io.signedIn := ~in.bits.typ(0)
      i2f.io.in := intValue
      i2f.io.roundingMode := in.bits.rm
      i2f.io.detectTininess := hardfloat.consts.tininess_afterRounding
      (sanitizeNaN(i2f.io.out, t), i2f.io.exceptionFlags)
    }

    val (data, exc) = i2fResults.unzip
    val dataPadded = data.init.map(d => Cat(data.last >> d.getWidth, d)) :+ data.last
    mux.data := dataPadded(tag)
    mux.exc := exc(tag)
  }

  io.out <> Pipe(in.valid, mux, latency-1)
}

class FPToFP(val latency: Int)(implicit p: Parameters) extends FPUModule()(p) {
  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val out = Valid(new FPResult)
    val lt = Bool(INPUT) // from FPToInt
  }

  val in = Pipe(io.in)

  val signNum = Mux(in.bits.rm(1), in.bits.in1 ^ in.bits.in2, Mux(in.bits.rm(0), ~in.bits.in2, in.bits.in2))
  val fsgnj = Cat(signNum(fLen), in.bits.in1(fLen-1, 0))

  val fsgnjMux = Wire(new FPResult)
  fsgnjMux.exc := UInt(0)
  fsgnjMux.data := fsgnj

  when (in.bits.wflags) { // fmin/fmax
    val isnan1 = maxType.isNaN(in.bits.in1)
    val isnan2 = maxType.isNaN(in.bits.in2)
    val isInvalid = maxType.isSNaN(in.bits.in1) || maxType.isSNaN(in.bits.in2)
    val isNaNOut = isnan1 && isnan2
    val isLHS = isnan2 || in.bits.rm(0) =/= io.lt && !isnan1
    fsgnjMux.exc := isInvalid << 4
    fsgnjMux.data := Mux(isNaNOut, maxType.qNaN, Mux(isLHS, in.bits.in1, in.bits.in2))
  }

  val inTag = !in.bits.singleIn // TODO typeTag
  val outTag = !in.bits.singleOut // TODO typeTag
  val mux = Wire(init = fsgnjMux)
  for (t <- floatTypes.init) {
    when (outTag === typeTag(t)) {
      mux.data := Cat(fsgnjMux.data >> t.recodedWidth, maxType.unsafeConvert(fsgnjMux.data, t))
    }
  }

  when (in.bits.wflags && !in.bits.ren2) { // fcvt
    if (floatTypes.size > 1) {
      // widening conversions simply canonicalize NaN operands
      val widened = Mux(maxType.isNaN(in.bits.in1), maxType.qNaN, in.bits.in1)
      fsgnjMux.data := widened
      fsgnjMux.exc := maxType.isSNaN(in.bits.in1) << 4

      // narrowing conversions require rounding (for RVQ, this could be
      // optimized to use a single variable-position rounding unit, rather
      // than two fixed-position ones)
      for (outType <- floatTypes.init) when (outTag === typeTag(outType) && (typeTag(outType) == 0 || outTag < inTag)) {
        val narrower = Module(new hardfloat.RecFNToRecFN(maxType.exp, maxType.sig, outType.exp, outType.sig))
        narrower.io.in := in.bits.in1
        narrower.io.roundingMode := in.bits.rm
        narrower.io.detectTininess := hardfloat.consts.tininess_afterRounding
        val narrowed = sanitizeNaN(narrower.io.out, outType)
        mux.data := Cat(fsgnjMux.data >> narrowed.getWidth, narrowed)
        mux.exc := narrower.io.exceptionFlags
      }
    }
  }

  io.out <> Pipe(in.valid, mux, latency-1)
}

class MulAddRecFNPipe(latency: Int, expWidth: Int, sigWidth: Int) extends Module
{
    require(latency<=2) 

    val io = new Bundle {
        val validin = Bool(INPUT)
        val op = Bits(INPUT, 2)
        val a = Bits(INPUT, expWidth + sigWidth + 1)
        val b = Bits(INPUT, expWidth + sigWidth + 1)
        val c = Bits(INPUT, expWidth + sigWidth + 1)
        val roundingMode   = UInt(INPUT, 3)
        val detectTininess = UInt(INPUT, 1)
        val out = Bits(OUTPUT, expWidth + sigWidth + 1)
        val exceptionFlags = Bits(OUTPUT, 5)
        val validout = Bool(OUTPUT)
    }

    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val mulAddRecFNToRaw_preMul =
        Module(new hardfloat.MulAddRecFNToRaw_preMul(expWidth, sigWidth))
    val mulAddRecFNToRaw_postMul =
        Module(new hardfloat.MulAddRecFNToRaw_postMul(expWidth, sigWidth))

    mulAddRecFNToRaw_preMul.io.op := io.op
    mulAddRecFNToRaw_preMul.io.a  := io.a
    mulAddRecFNToRaw_preMul.io.b  := io.b
    mulAddRecFNToRaw_preMul.io.c  := io.c

    val mulAddResult =
        (mulAddRecFNToRaw_preMul.io.mulAddA *
             mulAddRecFNToRaw_preMul.io.mulAddB) +&
            mulAddRecFNToRaw_preMul.io.mulAddC

    val valid_stage0 = Wire(Bool())
    val roundingMode_stage0 = Wire(UInt(width=3))
    val detectTininess_stage0 = Wire(UInt(width=1))
  
    val postmul_regs = if(latency>0) 1 else 0
    mulAddRecFNToRaw_postMul.io.fromPreMul   := Pipe(io.validin, mulAddRecFNToRaw_preMul.io.toPostMul, postmul_regs).bits
    mulAddRecFNToRaw_postMul.io.mulAddResult := Pipe(io.validin, mulAddResult, postmul_regs).bits
    mulAddRecFNToRaw_postMul.io.roundingMode := Pipe(io.validin, io.roundingMode, postmul_regs).bits
    roundingMode_stage0                      := Pipe(io.validin, io.roundingMode, postmul_regs).bits
    detectTininess_stage0                    := Pipe(io.validin, io.detectTininess, postmul_regs).bits
    valid_stage0                             := Pipe(io.validin, false.B, postmul_regs).valid
    
    //------------------------------------------------------------------------
    //------------------------------------------------------------------------
    val roundRawFNToRecFN = Module(new hardfloat.RoundRawFNToRecFN(expWidth, sigWidth, 0))

    val round_regs = if(latency==2) 1 else 0
    roundRawFNToRecFN.io.invalidExc         := Pipe(valid_stage0, mulAddRecFNToRaw_postMul.io.invalidExc, round_regs).bits
    roundRawFNToRecFN.io.in                 := Pipe(valid_stage0, mulAddRecFNToRaw_postMul.io.rawOut, round_regs).bits
    roundRawFNToRecFN.io.roundingMode       := Pipe(valid_stage0, roundingMode_stage0, round_regs).bits
    roundRawFNToRecFN.io.detectTininess     := Pipe(valid_stage0, detectTininess_stage0, round_regs).bits
    io.validout                             := Pipe(valid_stage0, false.B, round_regs).valid

    roundRawFNToRecFN.io.infiniteExc := Bool(false)

    io.out            := roundRawFNToRecFN.io.out
    io.exceptionFlags := roundRawFNToRecFN.io.exceptionFlags
}

class FPUFMAPipe(val latency: Int, val t: FType)(implicit p: Parameters) extends FPUModule()(p) {
  require(latency>0)

  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val out = Valid(new FPResult) 
  }

  val valid = Reg(next=io.in.valid)
  val in = Reg(new FPInput)
  when (io.in.valid) {
    val one = UInt(1) << (t.sig + t.exp - 1)
    val zero = (io.in.bits.in1 ^ io.in.bits.in2) & (UInt(1) << (t.sig + t.exp))
    val cmd_fma = io.in.bits.ren3
    val cmd_addsub = io.in.bits.swap23
    in := io.in.bits
    when (cmd_addsub) { in.in2 := one }
    when (!(cmd_fma || cmd_addsub)) { in.in3 := zero }
  }

  val fma = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  fma.io.validin := valid
  fma.io.op := in.fmaCmd
  fma.io.roundingMode := in.rm
  fma.io.detectTininess := hardfloat.consts.tininess_afterRounding
  fma.io.a := in.in1
  fma.io.b := in.in2
  fma.io.c := in.in3

  val res = Wire(new FPResult)
  res.data := sanitizeNaN(fma.io.out, t)
  res.exc := fma.io.exceptionFlags

  io.out := Pipe(fma.io.validout, res, (latency-3) max 0)
}

class FPUDistPipe(val latency: Int, val t: FType)(implicit p: Parameters) extends FPUModule()(p) {
  require(latency>0)

  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val out = Valid(new FPResult)
    val coord = (new FPDistInput).flip
  }

  val valid = Reg(next=io.in.valid)
  val in = Reg(new FPInput)

  val one = UInt(1) << (t.sig + t.exp - 1)
  val zero = (io.in.bits.in1 ^ io.in.bits.in2) & (UInt(1) << (t.sig + t.exp))
  in := io.in.bits

  // when (valid) {
  //   printf("io.coord.x1: %x\n", io.coord.x1)
  //   printf("io.coord.y1: %x\n", io.coord.y1)
  //   printf("io.coord.z1: %x\n", io.coord.z1)

  //   printf("io.coord.x2: %x\n", io.coord.x2)
  //   printf("io.coord.y2: %x\n", io.coord.y2)
  //   printf("io.coord.z2: %x\n", io.coord.z2)
  // }

  val sub1_s1 = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  sub1_s1.io.validin := valid
  sub1_s1.io.op := 0x01 // SUB
  sub1_s1.io.roundingMode := in.rm
  sub1_s1.io.detectTininess := hardfloat.consts.tininess_afterRounding
  sub1_s1.io.a := /*in.in1*/ io.coord.x1
  sub1_s1.io.b := one
  sub1_s1.io.c := /*in.in2*/ io.coord.x2

  val mult1_s2 = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  mult1_s2.io.validin := Pipe(sub1_s1.io.validout, sub1_s1.io.out, 1).valid
  mult1_s2.io.op := 0x00 // ADD / MUL
  mult1_s2.io.roundingMode := Pipe(sub1_s1.io.validout, in.rm, 1).bits
  mult1_s2.io.detectTininess := Pipe(sub1_s1.io.validout, hardfloat.consts.tininess_afterRounding, 1).bits
  mult1_s2.io.a := Pipe(sub1_s1.io.validout, sanitizeNaN(sub1_s1.io.out, t), 1).bits
  mult1_s2.io.b := Pipe(sub1_s1.io.validout, sanitizeNaN(sub1_s1.io.out, t), 1).bits
  mult1_s2.io.c := Pipe(sub1_s1.io.validout, zero, 1).bits

  val sub2_s1 = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  sub2_s1.io.validin := valid
  sub2_s1.io.op := 0x01 // SUB
  sub2_s1.io.roundingMode := in.rm
  sub2_s1.io.detectTininess := hardfloat.consts.tininess_afterRounding
  sub2_s1.io.a := /*in.in1*/ io.coord.y1
  sub2_s1.io.b := one
  sub2_s1.io.c := /*in.in2*/ io.coord.y2

  val mult2_s2 = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  mult2_s2.io.validin := Pipe(sub2_s1.io.validout, sub2_s1.io.out, 1).valid
  mult2_s2.io.op := 0x00 // ADD / MUL
  mult2_s2.io.roundingMode := Pipe(sub2_s1.io.validout, in.rm, 1).bits
  mult2_s2.io.detectTininess := Pipe(sub2_s1.io.validout, hardfloat.consts.tininess_afterRounding, 1).bits
  mult2_s2.io.a := Pipe(sub2_s1.io.validout, sanitizeNaN(sub2_s1.io.out, t), 1).bits
  mult2_s2.io.b := Pipe(sub2_s1.io.validout, sanitizeNaN(sub2_s1.io.out, t), 1).bits
  mult2_s2.io.c := Pipe(sub2_s1.io.validout, zero, 1).bits

  val add1_s3 = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  add1_s3.io.validin := Pipe(mult1_s2.io.validout &  mult2_s2.io.validout, mult1_s2.io.out &  mult2_s2.io.out, 1).valid
  add1_s3.io.op := 0x00 // ADD
  add1_s3.io.roundingMode := Pipe(mult1_s2.io.validout &  mult2_s2.io.validout, in.rm, 1).bits
  add1_s3.io.detectTininess := Pipe(mult1_s2.io.validout &  mult2_s2.io.validout, hardfloat.consts.tininess_afterRounding, 1).bits
  add1_s3.io.a := Pipe(mult1_s2.io.validout &  mult2_s2.io.validout, sanitizeNaN(mult1_s2.io.out, t), 1).bits
  add1_s3.io.b := Pipe(mult1_s2.io.validout &  mult2_s2.io.validout, one, 1).bits
  add1_s3.io.c := Pipe(mult1_s2.io.validout &  mult2_s2.io.validout, sanitizeNaN(mult2_s2.io.out, t), 1).bits

  val sub3_s1 = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  sub3_s1.io.validin := valid
  sub3_s1.io.op := 0x01 // SUB
  sub3_s1.io.roundingMode := in.rm
  sub3_s1.io.detectTininess := hardfloat.consts.tininess_afterRounding
  sub3_s1.io.a := /*in.in1*/ io.coord.z1
  sub3_s1.io.b := one
  sub3_s1.io.c := /*in.in2*/ io.coord.z2

  val mult3_s2 = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  mult3_s2.io.validin := Pipe(sub3_s1.io.validout, sub3_s1.io.out, 1).valid
  mult3_s2.io.op := 0x00 // ADD / MUL
  mult3_s2.io.roundingMode := Pipe(sub3_s1.io.validout, in.rm, 1).bits
  mult3_s2.io.detectTininess := Pipe(sub3_s1.io.validout, hardfloat.consts.tininess_afterRounding, 1).bits
  mult3_s2.io.a := Pipe(sub3_s1.io.validout, sanitizeNaN(sub3_s1.io.out, t), 1).bits
  mult3_s2.io.b := Pipe(sub3_s1.io.validout, sanitizeNaN(sub3_s1.io.out, t), 1).bits
  mult3_s2.io.c := Pipe(sub3_s1.io.validout, zero, 1).bits

  val add2_s3 = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  add2_s3.io.validin := Pipe(mult3_s2.io.validout, mult3_s2.io.out, 1).valid
  add2_s3.io.op := 0x00 // ADD
  add2_s3.io.roundingMode := Pipe(mult3_s2.io.validout, in.rm, 1).bits
  add2_s3.io.detectTininess := Pipe(mult3_s2.io.validout, hardfloat.consts.tininess_afterRounding, 1).bits
  add2_s3.io.a := Pipe(mult3_s2.io.validout, sanitizeNaN(mult3_s2.io.out, t), 1).bits
  add2_s3.io.b := Pipe(mult3_s2.io.validout, one, 1).bits
  add2_s3.io.c := Pipe(mult3_s2.io.validout, zero, 1).bits

  val add1_s4 = Module(new MulAddRecFNPipe((latency-1) min 2, t.exp, t.sig))
  add1_s4.io.validin := Pipe(add1_s3.io.validout & add2_s3.io.validout, add1_s3.io.out & add2_s3.io.out, 1).valid
  add1_s4.io.op := 0x00 // ADD
  add1_s4.io.roundingMode := Pipe(add1_s3.io.validout & add2_s3.io.validout, in.rm, 1).bits
  add1_s4.io.detectTininess := Pipe(add1_s3.io.validout & add2_s3.io.validout, hardfloat.consts.tininess_afterRounding, 1).bits
  add1_s4.io.a := Pipe(add1_s3.io.validout & add2_s3.io.validout, sanitizeNaN(add1_s3.io.out, t), 1).bits
  add1_s4.io.b := Pipe(add1_s3.io.validout & add2_s3.io.validout, one, 1).bits
  add1_s4.io.c := Pipe(add1_s3.io.validout & add2_s3.io.validout, sanitizeNaN(add2_s3.io.out, t), 1).bits

  val distSqrt = Module(new hardfloat.DivSqrtRecFN_small(t.exp, t.sig, 0))
  distSqrt.io.inValid := Pipe(add1_s4.io.validout, add1_s4.io.out, 1).valid
  distSqrt.io.sqrtOp := Bool(true)
  // distSqrt.io.a := Pipe(add1_s4.io.validout, sanitizeNaN(add1_s4.io.out, t), 1).bits(31,0)
  distSqrt.io.a := Pipe(add1_s4.io.validout, sanitizeNaN(add1_s4.io.out, t), 1).bits
  distSqrt.io.b := UInt("b00000000000000000000000000000000")
  distSqrt.io.roundingMode := in.rm
  distSqrt.io.detectTininess := hardfloat.consts.tininess_afterRounding

  // when (add1_s4.io.validout) {
  //   printf("sanitizeNaN(add1_s4.io.out, t) 0x%x\n", sanitizeNaN(add1_s4.io.out, t))
  //   printf("in.in2 0x%x\n", in.in2)
  // }

  // when (distSqrt.io.outValid_sqrt) {
  //   printf("distSqrt %x\n", sanitizeNaN(distSqrt.io.out, t))
  //   printf(s"distSqrt FINISHED ${distSqrt.io}\n")
  // }

  // Random print statements for debugging purposes..
  // when (sub1_s1.io.validout) { printf("sub1_s1: %x\n", sanitizeNaN(sub1_s1.io.out, t)) }
  // when (mult1_s2.io.validout) { printf("mult1_s2: %x\n", sanitizeNaN(mult1_s2.io.out, t)) }
  // when (sub2_s1.io.validout) { printf("sub2_s1: %x\n", sanitizeNaN(sub2_s1.io.out, t)) }
  // when (mult2_s2.io.validout) { printf("mult2_s2: %x\n", sanitizeNaN(mult2_s2.io.out, t)) }
  // when (add1_s3.io.validout) { printf("add1_s3: %x\n", sanitizeNaN(add1_s3.io.out, t)) }
  // when (sub3_s1.io.validout) { printf("sub3_s1: %x\n", sanitizeNaN(sub3_s1.io.out, t)) }
  // when (mult3_s2.io.validout) { printf("mult3_s2: %x\n", sanitizeNaN(mult3_s2.io.out, t)) }
  // when (add2_s3.io.validout) { printf("add2_s3: %x\n", sanitizeNaN(add2_s3.io.out, t)) }
  // when (add1_s4.io.validout) { printf("add1_s4: %x\n", sanitizeNaN(add1_s4.io.out, t)) }

  val res = Wire(new FPResult)
  res.data := sanitizeNaN(distSqrt.io.out, t)
  res.exc := distSqrt.io.exceptionFlags

  io.out := Pipe(distSqrt.io.outValid_sqrt, res, (latency-30) max 0)
}

class FPU(cfg: FPUParams)(implicit p: Parameters) extends FPUModule()(p) {
  val io = new FPUIO

  val ex_reg_valid = Reg(next=io.valid, init=Bool(false))
  val req_valid = ex_reg_valid || io.cp_req.valid
  val ex_reg_inst = RegEnable(io.inst, io.valid)
  val ex_cp_valid = io.cp_req.fire()
  val mem_cp_valid = Reg(next=ex_cp_valid, init=Bool(false))
  val wb_cp_valid = Reg(next=mem_cp_valid, init=Bool(false))
  val mem_reg_valid = RegInit(false.B)
  val killm = (io.killm || io.nack_mem) && !mem_cp_valid
  // Kill X-stage instruction if M-stage is killed.  This prevents it from
  // speculatively being sent to the div-sqrt unit, which can cause priority
  // inversion for two back-to-back divides, the first of which is killed.
  val killx = io.killx || mem_reg_valid && killm
  mem_reg_valid := ex_reg_valid && !killx || ex_cp_valid
  val mem_reg_inst = RegEnable(ex_reg_inst, ex_reg_valid)
  val wb_reg_valid = Reg(next=mem_reg_valid && (!killm || mem_cp_valid), init=Bool(false))

  val fp_decoder = Module(new FPUDecoder)
  fp_decoder.io.inst := io.inst

  val cp_ctrl = Wire(new FPUCtrlSigs)
  cp_ctrl <> io.cp_req.bits
  io.cp_resp.valid := Bool(false)
  io.cp_resp.bits.data := UInt(0)

  val id_ctrl = fp_decoder.io.sigs
  val ex_ctrl = Mux(ex_cp_valid, cp_ctrl, RegEnable(id_ctrl, io.valid))
  val mem_ctrl = RegEnable(ex_ctrl, req_valid)
  val wb_ctrl = RegEnable(mem_ctrl, mem_reg_valid)

  // load response
  val load_wb = Reg(next=io.dmem_resp_val)
  val load_wb_double = RegEnable(io.dmem_resp_type(0), io.dmem_resp_val)
  val load_wb_data = RegEnable(io.dmem_resp_data, io.dmem_resp_val)
  val load_wb_tag = RegEnable(io.dmem_resp_tag, io.dmem_resp_val)

  // when (io.dmem_resp_val) { printf("io.dmem_resp_val\n") }

  // regfile
  val regfile = Mem(32, Bits(width = fLen+1))
  when (load_wb) {
    val wdata = recode(load_wb_data, load_wb_double)
    regfile(load_wb_tag) := wdata
    assert(consistent(wdata))
    if (enableCommitLog)
      printf("f%d p%d 0x%x\n", load_wb_tag, load_wb_tag + 32, load_wb_data)
  }

  val ex_ra = List.fill(3)(Reg(UInt()))
  val ex_rs = ex_ra.map(a => regfile(a))
  when (io.valid) {
    when (id_ctrl.ren1) {
      when (!id_ctrl.swap12) { ex_ra(0) := io.inst(19,15) }
      when (id_ctrl.swap12) { ex_ra(1) := io.inst(19,15) }
    }
    when (id_ctrl.ren2) {
      when (id_ctrl.swap12) { ex_ra(0) := io.inst(24,20) }
      when (id_ctrl.swap23) { ex_ra(2) := io.inst(24,20) }
      when (!id_ctrl.swap12 && !id_ctrl.swap23) { ex_ra(1) := io.inst(24,20) }
    }
    when (id_ctrl.ren3) { ex_ra(2) := io.inst(31,27) }
  }
  val ex_rm = Mux(ex_reg_inst(14,12) === Bits(7), io.fcsr_rm, ex_reg_inst(14,12))

  val sfma = Module(new FPUFMAPipe(cfg.sfmaLatency, FType.S))
  sfma.io.in.valid := req_valid && ex_ctrl.fma && ex_ctrl.singleOut
  sfma.io.in.bits := fuInput(Some(sfma.t))

  val ex_ra_coord = List.fill(6)(Reg(UInt()))
  val ex_rs_coord = ex_ra_coord.map(a => regfile(a))

  when (io.valid) {
    ex_ra_coord(0) := 10
    ex_ra_coord(1) := 11
    ex_ra_coord(2) := 12
    ex_ra_coord(3) := 13
    ex_ra_coord(4) := 14
    ex_ra_coord(5) := 15
  }

  val sfdist = Module(new FPUDistPipe(cfg.sfdistLatency, FType.S))
  sfdist.io.in.valid := req_valid && ex_ctrl.fdist && ex_ctrl.singleOut
  sfdist.io.in.bits := fuInput(Some(sfdist.t))
  sfdist.io.coord := fuDistInput(Some(sfdist.t))

  val fpiu = Module(new FPToInt)
  fpiu.io.in.valid := req_valid && (ex_ctrl.toint || ex_ctrl.div || ex_ctrl.sqrt || (ex_ctrl.fastpipe && ex_ctrl.wflags))
  fpiu.io.in.bits := fuInput(None)
  io.store_data := fpiu.io.out.bits.store
  io.toint_data := fpiu.io.out.bits.toint
  when(fpiu.io.out.valid && mem_cp_valid && mem_ctrl.toint){
    io.cp_resp.bits.data := fpiu.io.out.bits.toint
    io.cp_resp.valid := Bool(true)
  }

  val ifpu = Module(new IntToFP(2))
  ifpu.io.in.valid := req_valid && ex_ctrl.fromint
  ifpu.io.in.bits := fpiu.io.in.bits
  ifpu.io.in.bits.in1 := Mux(ex_cp_valid, io.cp_req.bits.in1, io.fromint_data)

  val fpmu = Module(new FPToFP(2))
  fpmu.io.in.valid := req_valid && ex_ctrl.fastpipe
  fpmu.io.in.bits := fpiu.io.in.bits
  fpmu.io.lt := fpiu.io.out.bits.lt

  val divSqrt_wen = Wire(init = false.B)
  val divSqrt_inFlight = Wire(init = false.B)
  val divSqrt_waddr = Reg(UInt(width = 5))
  val divSqrt_typeTag = Wire(UInt(width = log2Up(floatTypes.size)))
  val divSqrt_wdata = Wire(UInt(width = fLen+1))
  val divSqrt_flags = Wire(UInt(width = FPConstants.FLAGS_SZ))

  // writeback arbitration
  case class Pipe(p: Module, lat: Int, cond: (FPUCtrlSigs) => Bool, res: FPResult)
  val pipes = List(
    Pipe(fpmu, fpmu.latency, (c: FPUCtrlSigs) => c.fastpipe, fpmu.io.out.bits),
    Pipe(ifpu, ifpu.latency, (c: FPUCtrlSigs) => c.fromint, ifpu.io.out.bits),
    Pipe(sfma, sfma.latency, (c: FPUCtrlSigs) => c.fma && c.singleOut, sfma.io.out.bits),
    Pipe(sfdist, sfdist.latency, (c: FPUCtrlSigs) => c.fdist && c.singleOut, sfdist.io.out.bits)) ++
    (fLen > 32).option({
          val dfma = Module(new FPUFMAPipe(cfg.dfmaLatency, FType.D))
          dfma.io.in.valid := req_valid && ex_ctrl.fma && !ex_ctrl.singleOut
          dfma.io.in.bits := fuInput(Some(dfma.t))
          Pipe(dfma, dfma.latency, (c: FPUCtrlSigs) => c.fma && !c.singleOut, dfma.io.out.bits)
        })
  def latencyMask(c: FPUCtrlSigs, offset: Int) = {
    require(pipes.forall(_.lat >= offset))
    pipes.map(p => Mux(p.cond(c), UInt(1 << p.lat-offset), UInt(0))).reduce(_|_)
  }
  def pipeid(c: FPUCtrlSigs) = pipes.zipWithIndex.map(p => Mux(p._1.cond(c), UInt(p._2), UInt(0))).reduce(_|_)
  val maxLatency = pipes.map(_.lat).max
  val memLatencyMask = latencyMask(mem_ctrl, 2)

  class WBInfo extends Bundle {
    val rd = UInt(width = 5)
    val single = Bool()
    val cp = Bool()
    val pipeid = UInt(width = log2Ceil(pipes.size))
    override def cloneType: this.type = new WBInfo().asInstanceOf[this.type]
  }

  val wen = Reg(init=Bits(0, maxLatency-1))
  val wbInfo = Reg(Vec(maxLatency-1, new WBInfo))
  val mem_wen = mem_reg_valid && (mem_ctrl.fma || mem_ctrl.fdist || mem_ctrl.fastpipe || mem_ctrl.fromint)
  val write_port_busy = RegEnable(mem_wen && (memLatencyMask & latencyMask(ex_ctrl, 1)).orR || (wen & latencyMask(ex_ctrl, 0)).orR, req_valid)

  for (i <- 0 until maxLatency-2) {
    when (wen(i+1)) { wbInfo(i) := wbInfo(i+1) }
  }
  wen := wen >> 1
  when (mem_wen) {
    when (!killm) {
      wen := wen >> 1 | memLatencyMask
    }
    for (i <- 0 until maxLatency-1) {
      when (!write_port_busy && memLatencyMask(i)) {
        wbInfo(i).cp := mem_cp_valid
        wbInfo(i).single := mem_ctrl.singleOut
        wbInfo(i).pipeid := pipeid(mem_ctrl)
        wbInfo(i).rd := mem_reg_inst(11,7)
      }
    }
  }

  val waddr = Mux(divSqrt_wen, divSqrt_waddr, wbInfo(0).rd)
  val wdouble = Mux(divSqrt_wen, divSqrt_typeTag, !wbInfo(0).single)
  val wdata = box(Mux(divSqrt_wen, divSqrt_wdata, (pipes.map(_.res.data): Seq[UInt])(wbInfo(0).pipeid)), wdouble)
  val wexc = (pipes.map(_.res.exc): Seq[UInt])(wbInfo(0).pipeid)
  when ((!wbInfo(0).cp && wen(0)) || divSqrt_wen) {
    assert(consistent(wdata))
    regfile(waddr) := wdata
    if (enableCommitLog) {
      printf("f%d p%d 0x%x\n", waddr, waddr + 32, ieee(wdata))
    }
  }
  when (wbInfo(0).cp && wen(0)) {
    io.cp_resp.bits.data := wdata
    io.cp_resp.valid := Bool(true)
  }
  io.cp_req.ready := !ex_reg_valid

  val wb_toint_valid = wb_reg_valid && wb_ctrl.toint
  val wb_toint_exc = RegEnable(fpiu.io.out.bits.exc, mem_ctrl.toint)
  io.fcsr_flags.valid := wb_toint_valid || divSqrt_wen || wen(0)
  io.fcsr_flags.bits :=
    Mux(wb_toint_valid, wb_toint_exc, UInt(0)) |
    Mux(divSqrt_wen, divSqrt_flags, UInt(0)) |
    Mux(wen(0), wexc, UInt(0))

  val divSqrt_write_port_busy = (mem_ctrl.div || mem_ctrl.sqrt) && wen.orR
  io.fcsr_rdy := !(ex_reg_valid && ex_ctrl.wflags || mem_reg_valid && mem_ctrl.wflags || wb_reg_valid && wb_ctrl.toint || wen.orR || divSqrt_inFlight)
  io.nack_mem := write_port_busy || divSqrt_write_port_busy || divSqrt_inFlight
  io.dec <> fp_decoder.io.sigs
  def useScoreboard(f: ((Pipe, Int)) => Bool) = pipes.zipWithIndex.filter(_._1.lat > 3).map(x => f(x)).fold(Bool(false))(_||_)
  io.sboard_set := wb_reg_valid && !wb_cp_valid && Reg(next=useScoreboard(_._1.cond(mem_ctrl)) || mem_ctrl.div || mem_ctrl.sqrt)
  io.sboard_clr := !wb_cp_valid && (divSqrt_wen || (wen(0) && useScoreboard(x => wbInfo(0).pipeid === UInt(x._2))))
  io.sboard_clra := waddr
  // we don't currently support round-max-magnitude (rm=4)
  io.illegal_rm := io.inst(14,12).isOneOf(5, 6) || io.inst(14,12) === 7 && io.fcsr_rm >= 5

  if (cfg.divSqrt) {
    val divSqrt_killed = Reg(Bool())

    for (t <- floatTypes) {
      val tag = !mem_ctrl.singleOut // TODO typeTag
      val divSqrt = Module(new hardfloat.DivSqrtRecFN_small(t.exp, t.sig, 0))
      divSqrt.io.inValid := mem_reg_valid && tag === typeTag(t) && (mem_ctrl.div || mem_ctrl.sqrt) && !divSqrt_inFlight
      divSqrt.io.sqrtOp := mem_ctrl.sqrt
      divSqrt.io.a := maxType.unsafeConvert(fpiu.io.out.bits.in.in1, t)
      divSqrt.io.b := maxType.unsafeConvert(fpiu.io.out.bits.in.in2, t)
      divSqrt.io.roundingMode := fpiu.io.out.bits.in.rm
      divSqrt.io.detectTininess := hardfloat.consts.tininess_afterRounding

      when (!divSqrt.io.inReady) { divSqrt_inFlight := true } // only 1 in flight

      when (divSqrt.io.inValid && divSqrt.io.inReady) {
        divSqrt_killed := killm
        divSqrt_waddr := mem_reg_inst(11,7)
      }

      when (divSqrt.io.outValid_div || divSqrt.io.outValid_sqrt) {
        divSqrt_wen := !divSqrt_killed
        divSqrt_wdata := sanitizeNaN(divSqrt.io.out, t)
        divSqrt_flags := divSqrt.io.exceptionFlags
        divSqrt_typeTag := typeTag(t)
      }
    }
  } else {
    when (id_ctrl.div || id_ctrl.sqrt) { io.illegal_rm := true }
  }

  def fuInput(minT: Option[FType]): FPInput = {
    val req = Wire(new FPInput)
    val tag = !ex_ctrl.singleIn // TODO typeTag
    req := ex_ctrl
    req.rm := ex_rm
    req.in1 := unbox(ex_rs(0), tag, minT)
    req.in2 := unbox(ex_rs(1), tag, minT)
    req.in3 := unbox(ex_rs(2), tag, minT)
    req.typ := ex_reg_inst(21,20)
    req.fmaCmd := ex_reg_inst(3,2) | (!ex_ctrl.ren3 && ex_reg_inst(27))
    when (ex_cp_valid) {
      req := io.cp_req.bits
      when (io.cp_req.bits.swap23) {
        req.in2 := io.cp_req.bits.in3
        req.in3 := io.cp_req.bits.in2
      }
    }
    req
  }

  def fuDistInput(minT: Option[FType]): FPDistInput = {
    val req = Wire(new FPDistInput)
    val tag = !ex_ctrl.singleIn // TODO typeTag
    req := ex_ctrl
    req.x1 := unbox(ex_rs_coord(0), tag, minT)
    req.y1 := unbox(ex_rs_coord(1), tag, minT)
    req.z1 := unbox(ex_rs_coord(2), tag, minT)
    req.x2 := unbox(ex_rs_coord(3), tag, minT)
    req.y2 := unbox(ex_rs_coord(4), tag, minT)
    req.z2 := unbox(ex_rs_coord(5), tag, minT)

    when (ex_cp_valid) {
      req := io.cp_req.bits
    }
    req
  }
}

/** Mix-ins for constructing tiles that may have an FPU external to the core pipeline */
trait CanHaveSharedFPU extends HasTileParameters

trait CanHaveSharedFPUModule {
  val outer: CanHaveSharedFPU
  val fpuOpt = outer.tileParams.core.fpu.map(params => Module(new FPU(params)(outer.p)))
  // TODO fpArb could go here instead of inside LegacyRoccComplex
}
