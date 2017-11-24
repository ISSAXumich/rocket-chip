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

// case class NewInstParams(
//   mulUnroll: Int = 1,
//   divUnroll: Int = 1,
//   mulEarlyOut: Boolean = false,
//   divEarlyOut: Boolean = false
// )

class NewInst(width: Int, nXpr: Int = 32) extends Module {
  val io = new NewInstIO(width, log2Up(nXpr))
  val w = io.req.bits.in1.getWidth
 
  val s_ready :: s_mul :: s_done_mul :: Nil = Enum(UInt(), 3)
  val state = Reg(init=s_ready)
 
  val req = Reg(io.req.bits)
  val count = Reg(UInt(width=w))

  require(w == 32 || w == 64)

  val result = Reg(UInt(width=w))

  when (state === s_mul) {
    result := io.req.bits.in1 + io.req.bits.in2

    count := count - 1

    when (count === 0) {
      state := s_done_mul
    }
  }
  when (io.resp.fire() || io.kill) {
    state := s_ready
  }
  when (io.req.fire()) {
    state := s_mul
    count := Mux((io.req.bits.in1 > 0), io.req.bits.in1, 1)
    req := io.req.bits
  }

  io.resp.bits <> req
  io.resp.bits.data := result
  io.resp.valid := (state === s_done_mul)
  io.req.ready := state === s_ready
}
