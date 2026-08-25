package com.example.domain.engine

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * A lightweight, pure Kotlin GIF89a multi-frame encoder
 * optimized for quantized multi-frame black-and-white / color QR frames.
 */
class GifEncoder {
    private var width = 0
    private var height = 0
    private var delayMs = 200
    private var repeatCount = 0 // 0 = loop forever
    private val frames = mutableListOf<Bitmap>()

    fun setDelay(ms: Int) {
        delayMs = ms
    }

    fun setRepeat(count: Int) {
        repeatCount = count
    }

    fun addFrame(bitmap: Bitmap) {
        frames.add(bitmap)
        if (width == 0) {
            width = bitmap.width
            height = bitmap.height
        }
    }

    fun encode(outputStream: OutputStream): Boolean {
        if (frames.isEmpty()) return false
        try {
            // Header
            outputStream.write("GIF89a".toByteArray(Charsets.US_ASCII))

            // Logical Screen Descriptor
            writeShort(outputStream, width)
            writeShort(outputStream, height)
            outputStream.write(0x70) // GCT flag 0, color res 7, sort 0, gct size 0
            outputStream.write(0)    // Bg color index
            outputStream.write(0)    // Pixel aspect ratio

            // Netscape App Extension for Looping
            if (repeatCount >= 0) {
                outputStream.write(0x21) // Extension Introducer
                outputStream.write(0xFF) // App Extension Label
                outputStream.write(11)   // Block Size
                outputStream.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
                outputStream.write(3)    // Sub-block size
                outputStream.write(1)    // Loop sub-block id
                writeShort(outputStream, repeatCount)
                outputStream.write(0)    // Block Terminator
            }

            for (frame in frames) {
                writeFrame(frame, outputStream)
            }

            outputStream.write(0x3B) // GIF Trailer
            outputStream.flush()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun writeFrame(bitmap: Bitmap, out: OutputStream) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Quantize frame to a simple palette (max 256 colors)
        val colorMap = LinkedHashMap<Int, Int>()
        val indexedPixels = ByteArray(w * h)

        for (i in pixels.indices) {
            val color = pixels[i] and 0x00FFFFFF // ignore alpha for standard GIF
            if (!colorMap.containsKey(color)) {
                if (colorMap.size < 256) {
                    colorMap[color] = colorMap.size
                }
            }
            indexedPixels[i] = (colorMap[color] ?: 0).toByte()
        }

        val paletteSize = Math.max(2, 1 shl ceilLog2(colorMap.size))
        val paletteBits = ceilLog2(paletteSize)

        // Graphic Control Extension
        out.write(0x21) // Extension Introducer
        out.write(0xF9) // GCE Label
        out.write(4)    // Block size
        out.write(0x00) // Disposal method 0, no transparent color
        writeShort(out, delayMs / 10) // Delay in hundredths of second
        out.write(0)    // Transparent color index
        out.write(0)    // Terminator

        // Image Descriptor
        out.write(0x2C) // Image Separator
        writeShort(out, 0) // Left
        writeShort(out, 0) // Top
        writeShort(out, w)
        writeShort(out, h)
        // Local Color Table Flag (1), Interlace (0), Sort (0), Size of Local Table
        out.write(0x80 or (paletteBits - 1))

        // Write Local Color Table
        val paletteEntries = colorMap.keys.toList()
        for (i in 0 until paletteSize) {
            if (i < paletteEntries.size) {
                val c = paletteEntries[i]
                out.write((c shr 16) and 0xFF) // R
                out.write((c shr 8) and 0xFF)  // G
                out.write(c and 0xFF)         // B
            } else {
                out.write(0)
                out.write(0)
                out.write(0)
            }
        }

        // LZW Compression
        val minCodeSize = Math.max(2, paletteBits)
        out.write(minCodeSize)
        val lzw = LZWEncoder(w, h, indexedPixels, minCodeSize)
        lzw.encode(out)
        out.write(0) // Block terminator
    }

    private fun ceilLog2(n: Int): Int {
        var v = 1
        var count = 0
        while (v < n && count < 8) {
            v = v shl 1
            count++
        }
        return Math.max(1, count)
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private class LZWEncoder(
        private val imgW: Int,
        private val imgH: Int,
        private val pixAry: ByteArray,
        private val initCodeSize: Int
    ) {
        private var curPixel = 0
        private val maxBits = 12
        private val maxMaxCode = 1 shl maxBits
        private val hTab = IntArray(5003)
        private val codeTab = IntArray(5003)
        private val hSize = 5003
        private var freeEnt = 0
        private var clearFlg = false
        private var gInitBits = 0
        private var ClearCode = 0
        private var EOFCode = 0
        private var curAccum = 0
        private var curBits = 0
        private val accum = ByteArray(256)
        private var aCount = 0

        fun encode(os: OutputStream) {
            os.write(initCodeSize)
            gInitBits = initCodeSize
            clearFlg = false
            var nBits = gInitBits + 1
            ClearCode = 1 shl gInitBits
            EOFCode = ClearCode + 1
            freeEnt = ClearCode + 2
            aCount = 0
            var ent = nextPixel()
            var hShift = 0
            var fCode = hSize
            while (fCode < 65536) {
                hShift++
                fCode *= 2
            }
            hShift = 8 - hShift
            for (i in 0 until hSize) hTab[i] = -1

            output(ClearCode, nBits, os)

            var c = nextPixel()
            while (c != -1) {
                fCode = (c shl maxBits) + ent
                var i = (c shl hShift) xor ent
                if (hTab[i] == fCode) {
                    ent = codeTab[i]
                    c = nextPixel()
                    continue
                } else if (hTab[i] >= 0) {
                    var disp = hSize - i
                    if (i == 0) disp = 1
                    var found = false
                    while (true) {
                        i -= disp
                        if (i < 0) i += hSize
                        if (hTab[i] == fCode) {
                            ent = codeTab[i]
                            found = true
                            break
                        }
                        if (hTab[i] < 0) break
                    }
                    if (found) {
                        c = nextPixel()
                        continue
                    }
                }
                output(ent, nBits, os)
                ent = c
                if (freeEnt < maxMaxCode) {
                    codeTab[i] = freeEnt++
                    hTab[i] = fCode
                } else {
                    for (k in 0 until hSize) hTab[k] = -1
                    freeEnt = ClearCode + 2
                    clearFlg = true
                    output(ClearCode, nBits, os)
                }
                c = nextPixel()
            }
            output(ent, nBits, os)
            output(EOFCode, nBits, os)
        }

        private fun nextPixel(): Int {
            if (curPixel < pixAry.size) {
                return (pixAry[curPixel++].toInt() and 0xFF)
            }
            return -1
        }

        private fun output(code: Int, nBits: Int, os: OutputStream) {
            curAccum = curAccum or (code shl curBits)
            curBits += nBits
            while (curBits >= 8) {
                charOut((curAccum and 0xFF).toByte(), os)
                curAccum = curAccum shr 8
                curBits -= 8
            }
            if (freeEnt > ((1 shl nBits) - 1) || clearFlg) {
                if (clearFlg) {
                    clearFlg = false
                }
            }
            if (code == EOFCode) {
                while (curBits > 0) {
                    charOut((curAccum and 0xFF).toByte(), os)
                    curAccum = curAccum shr 8
                    curBits -= 8
                }
                flushChar(os)
            }
        }

        private fun charOut(c: Byte, os: OutputStream) {
            accum[aCount++] = c
            if (aCount >= 254) flushChar(os)
        }

        private fun flushChar(os: OutputStream) {
            if (aCount > 0) {
                os.write(aCount)
                os.write(accum, 0, aCount)
                aCount = 0
            }
        }
    }
}
