package com.example.domain.engine

import android.graphics.Bitmap
import java.io.OutputStream
import kotlin.math.max

/**
 * Memory-efficient, crash-safe streaming GIF89a encoder.
 * Streams each frame directly to an OutputStream buffer without storing
 * all bitmaps in memory at once.
 */
class StreamingGifEncoder {
    private var width = 0
    private var height = 0
    private var defaultDelayMs = 200
    private var outputStream: OutputStream? = null
    private var isStarted = false
    private var frameCount = 0

    // Reusable buffers to minimize GC allocations during multi-frame encoding
    private val lzwEncoder = LZWEncoder()

    fun start(
        out: OutputStream,
        width: Int,
        height: Int,
        delayMs: Int = 200,
        repeatCount: Int = 0 // 0 = loop forever
    ): Boolean {
        try {
            this.outputStream = out
            this.width = width
            this.height = height
            this.defaultDelayMs = delayMs
            this.frameCount = 0

            // 1. GIF Header
            writeString(out, "GIF89a")

            // 2. Logical Screen Descriptor
            writeShort(out, width)
            writeShort(out, height)
            out.write(0x70) // No Global Color Table, 8-bit color resolution
            out.write(0)    // Background Color Index
            out.write(0)    // Pixel Aspect Ratio

            // 3. Netscape Application Extension for Looping
            if (repeatCount >= 0) {
                out.write(0x21) // Extension Introducer
                out.write(0xFF) // Application Extension Label
                out.write(11)   // Block Size
                writeString(out, "NETSCAPE2.0")
                out.write(3)    // Sub-block Length
                out.write(1)    // Sub-block ID
                writeShort(out, repeatCount)
                out.write(0)    // Block Terminator
            }

            isStarted = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun addFrame(bitmap: Bitmap, customDelayMs: Int? = null): Boolean {
        if (!isStarted || outputStream == null) return false
        val out = outputStream ?: return false

        try {
            val frameDelay = customDelayMs ?: defaultDelayMs
            val scaled = if (bitmap.width != width || bitmap.height != height) {
                Bitmap.createScaledBitmap(bitmap, width, height, false)
            } else {
                bitmap
            }

            val w = scaled.width
            val h = scaled.height
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)

            if (scaled !== bitmap) {
                scaled.recycle()
            }

            // Build Color Palette (max 256 colors for GIF)
            val colorMap = LinkedHashMap<Int, Int>(64)
            val indexedPixels = ByteArray(w * h)

            for (i in pixels.indices) {
                val c = pixels[i] and 0x00FFFFFF
                var index = colorMap[c]
                if (index == null) {
                    if (colorMap.size < 256) {
                        index = colorMap.size
                        colorMap[c] = index
                    } else {
                        index = 0
                    }
                }
                indexedPixels[i] = index.toByte()
            }

            var pSize = 2
            while (pSize < colorMap.size && pSize < 256) {
                pSize = pSize shl 1
            }
            var paletteBits = 1
            while ((1 shl paletteBits) < pSize) {
                paletteBits++
            }
            if (paletteBits < 1) paletteBits = 1

            // 4. Graphic Control Extension
            out.write(0x21) // Extension Introducer
            out.write(0xF9) // GCE Label
            out.write(4)    // Block Size
            out.write(0x00) // Disposal Method: unspecified, User Input: 0, Transparent: 0
            writeShort(out, max(1, frameDelay / 10)) // Delay in 10ms units
            out.write(0)    // Transparent Color Index
            out.write(0)    // Block Terminator

            // 5. Image Descriptor
            out.write(0x2C) // Image Separator
            writeShort(out, 0) // Left
            writeShort(out, 0) // Top
            writeShort(out, w) // Width
            writeShort(out, h) // Height
            // Local Color Table Flag (1), Interlace (0), Sort (0), Size of Local Table
            out.write(0x80 or (paletteBits - 1))

            // 6. Local Color Table (RGB triplets)
            val paletteEntries = colorMap.keys.toList()
            for (i in 0 until pSize) {
                if (i < paletteEntries.size) {
                    val c = paletteEntries[i]
                    out.write((c shr 16) and 0xFF) // Red
                    out.write((c shr 8) and 0xFF)  // Green
                    out.write(c and 0xFF)         // Blue
                } else {
                    out.write(0)
                    out.write(0)
                    out.write(0)
                }
            }

            // 7. LZW Encoded Image Data
            val initCodeSize = max(2, paletteBits)
            lzwEncoder.encode(w, h, indexedPixels, initCodeSize, out)

            frameCount++
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun finish(): Boolean {
        if (!isStarted || outputStream == null) return false
        return try {
            val out = outputStream!!
            out.write(0x3B) // GIF Trailer
            out.flush()
            isStarted = false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun writeShort(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun writeString(out: OutputStream, s: String) {
        for (c in s) {
            out.write(c.code)
        }
    }

    /**
     * Efficient LZW Image Encoder with reusable memory structures.
     */
    private class LZWEncoder {
        private val BITS = 12
        private val HSIZE = 5003
        private val maxbits = BITS
        private val maxmaxcode = 1 shl BITS

        private val htab = IntArray(HSIZE)
        private val codetab = IntArray(HSIZE)
        private val accum = ByteArray(256)

        private var nBits = 0
        private var maxcode = 0
        private var freeEnt = 0
        private var clearFlg = false
        private var gInitBits = 0
        private var ClearCode = 0
        private var EOFCode = 0
        private var curAccum = 0
        private var curBits = 0
        private var aCount = 0

        private fun maxCode(n: Int): Int = (1 shl n) - 1

        private fun charOut(c: Byte, outs: OutputStream) {
            accum[aCount++] = c
            if (aCount >= 254) flushChar(outs)
        }

        private fun flushChar(outs: OutputStream) {
            if (aCount > 0) {
                outs.write(aCount)
                outs.write(accum, 0, aCount)
                aCount = 0
            }
        }

        private fun output(code: Int, outs: OutputStream) {
            curAccum = curAccum or (code shl curBits)
            curBits += nBits

            while (curBits >= 8) {
                charOut((curAccum and 0xFF).toByte(), outs)
                curAccum = curAccum shr 8
                curBits -= 8
            }

            if (freeEnt > maxcode || clearFlg) {
                if (clearFlg) {
                    nBits = gInitBits + 1
                    maxcode = maxCode(nBits)
                    clearFlg = false
                } else {
                    nBits++
                    maxcode = if (nBits == maxbits) maxmaxcode else maxCode(nBits)
                }
            }

            if (code == EOFCode) {
                while (curBits > 0) {
                    charOut((curAccum and 0xFF).toByte(), outs)
                    curAccum = curAccum shr 8
                    curBits -= 8
                }
                flushChar(outs)
            }
        }

        private fun clBlock(outs: OutputStream) {
            for (i in 0 until HSIZE) htab[i] = -1
            freeEnt = ClearCode + 2
            clearFlg = true
            output(ClearCode, outs)
        }

        fun encode(
            imgW: Int,
            imgH: Int,
            pixAry: ByteArray,
            initCodeSize: Int,
            os: OutputStream
        ) {
            var curPixel = 0
            os.write(initCodeSize)
            gInitBits = initCodeSize
            clearFlg = false
            nBits = gInitBits + 1
            maxcode = maxCode(nBits)
            ClearCode = 1 shl gInitBits
            EOFCode = ClearCode + 1
            freeEnt = ClearCode + 2
            aCount = 0
            curAccum = 0
            curBits = 0

            var ent = if (curPixel < pixAry.size) pixAry[curPixel++].toInt() and 0xFF else -1
            var hshift = 0
            var fcode = HSIZE
            while (fcode < 65536) {
                hshift++
                fcode *= 2
            }
            hshift = 8 - hshift

            for (i in 0 until HSIZE) htab[i] = -1

            output(ClearCode, os)

            while (curPixel < pixAry.size) {
                val c = pixAry[curPixel++].toInt() and 0xFF
                fcode = (c shl maxbits) + ent
                var i = ((c shl hshift) xor ent) % HSIZE
                if (i < 0) i += HSIZE

                if (htab[i] == fcode) {
                    ent = codetab[i]
                    continue
                } else if (htab[i] >= 0) {
                    var disp = HSIZE - i
                    if (i == 0) disp = 1
                    var found = false
                    while (true) {
                        i -= disp
                        if (i < 0) i += HSIZE
                        if (htab[i] == fcode) {
                            ent = codetab[i]
                            found = true
                            break
                        }
                        if (htab[i] < 0) break
                    }
                    if (found) {
                        continue
                    }
                }
                output(ent, os)
                ent = c
                if (freeEnt < maxmaxcode) {
                    codetab[i] = freeEnt++
                    htab[i] = fcode
                } else {
                    clBlock(os)
                }
            }
            if (ent != -1) {
                output(ent, os)
            }
            output(EOFCode, os)
            os.write(0) // write block terminator 0x00
        }
    }
}
