package ru.yuksanbo.common.uhc

abstract class Uhc {

    companion object {
        val EndOfStream = ByteArray(0)
    }

    fun execute(b: (UhcOperation.Builder) -> UhcOperation.Builder) {
        execute(b.invoke(UhcOperation.Builder()).build())
    }

    abstract fun execute(uhcOp: UhcOperation)

    abstract class StreamCallback {
        abstract fun onReceive(bytes: ByteArray)
    }
}