package com.example.domain.engine

import android.graphics.Bitmap
import java.io.OutputStream

/**
 * High-performance, crash-safe GIF89a multi-frame encoder
 * optimized for quantized multi-frame black-and-white / color QR frames.
 */
class GifEncoder {
    private var width = 0
    private var height = 0
    private var delayMs = 200 // in ms
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
            writeString(outputStream, "GIF89a")

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
                writeString(outputStream, "NETSCAPE2.0")
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

    private fun writeFrame(frame: Bitmap, out: OutputStream) {
        val scaled = if (frame.width != width || frame.height != height) {
            Bitmap.createScaledBitmap(frame, width, height, false)
        } else {
            frame
        }
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        // Build Color Palette (max 256 colors)
        val colorMap = LinkedHashMap<Int, Int>()
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

        // Graphic Control Extension
        out.write(0x21) // Extension Introducer
        out.write(0xF9) // GCE Label
        out.write(4)    // Block size
        out.write(0x00) // Disposal method 0, no transparent color
        writeShort(out, (delayMs / 10).coerceAtLeast(1)) // Delay in hundredths of second
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

        // Write Local Color Table (RGB triplets)
        val paletteEntries = colorMap.keys.toList()
        for (i in 0 until pSize) {
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
        val initCodeSize = Math.max(2, paletteBits)
        val lzw = LZWEncoder(w, h, indexedPixels, initCodeSize)
        lzw.encode(out)
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

    private class LZWEncoder(
        private val imgW: Int,
        private val imgH: Int,
        private val pixAry: ByteArray,
        private val initCodeSize: Int
    ) {
        private var curPixel = 0
        private val BITS = 12
        private val HSIZE = 5003
        private var nBits = 0
        private val maxbits = BITS
        private var maxcode = 0
        private val maxmaxcode = 1 shl BITS

        private val htab = IntArray(HSIZE)
        private val codetab = IntArray(HSIZE)

        private var freeEnt = 0
        private var clearFlg = false

        private var gInitBits = 0
        private var ClearCode = 0
        private var EOFCode = 0

        private var curAccum = 0
        private var curBits = 0

        private val accum = ByteArray(256)
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

        private fun nextPixel(): Int {
            if (curPixel < pixAry.size) {
                return pixAry[curPixel++].toInt() and 0xFF
            }
            return -1
        }

        fun encode(os: OutputStream) {
            os.write(initCodeSize)
            gInitBits = initCodeSize
            clearFlg = false
            nBits = gInitBits + 1
            maxcode = maxCode(nBits)
            ClearCode = 1 shl gInitBits
            EOFCode = ClearCode + 1
            freeEnt = ClearCode + 2
            aCount = 0

            var ent = nextPixel()
            var hshift = 0
            var fcode = HSIZE
            while (fcode < 65536) {
                hshift++
                fcode *= 2
            }
            hshift = 8 - hshift

            for (i in 0 until HSIZE) htab[i] = -1

            output(ClearCode, os)

            var c = nextPixel()
            while (c != -1) {
                fcode = (c shl maxbits) + ent
                var i = ((c shl hshift) xor ent) % HSIZE
                if (i < 0) i += HSIZE

                if (htab[i] == fcode) {
                    ent = codetab[i]
                    c = nextPixel()
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
                        c = nextPixel()
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
                c = nextPixel()
            }
            output(ent, os)
            output(EOFCode, os)
            os.write(0) // write block terminator 0x00
        }
    }
}
