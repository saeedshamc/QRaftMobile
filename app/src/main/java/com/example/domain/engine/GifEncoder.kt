package com.example.domain.engine

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance, memory-efficient, crash-safe streaming GIF89a encoder.
 *
 * Implements a buffer-based streaming pipeline that writes directly to an OutputStream
 * without hoarding all frames in memory.
 * Includes automatic input downscaling to maintain stability and prevent OOM crashes on large frames.
 */
class StreamingGifEncoder {

    companion object {
        const val MAX_SAFE_DIMENSION = 360
        const val DEFAULT_STREAM_BUFFER_SIZE = 65536 // 64 KB
    }

    private var width = 0
    private var height = 0
    private var defaultDelayMs = 200
    private var outputStream: BufferedOutputStream? = null
    private var isStarted = false
    private var frameCount = 0

    // Reusable buffers to minimize heap churn during frame processing
    private var reusablePixelBuffer: IntArray? = null
    private var reusableIndexedBuffer: ByteArray? = null
    private val lzwEncoder = LZWEncoder()

    // Fast primitive color palette lookup table (avoids boxing thousands of Integer objects per frame)
    private val colorKeys = IntArray(256)
    private val colorPalette = IntArray(256)
    private var paletteSize = 0

    /**
     * Initializes the GIF stream and writes header & logical screen descriptor.
     */
    fun start(
        out: OutputStream,
        width: Int,
        height: Int,
        delayMs: Int = 200,
        repeatCount: Int = 0 // 0 = loop forever
    ): Boolean {
        return try {
            // Apply safe maximum dimension bounds to prevent massive allocations
            val targetW = min(width, MAX_SAFE_DIMENSION).coerceAtLeast(64)
            val targetH = min(height, MAX_SAFE_DIMENSION).coerceAtLeast(64)

            this.outputStream = if (out is BufferedOutputStream) out else BufferedOutputStream(out, DEFAULT_STREAM_BUFFER_SIZE)
            this.width = targetW
            this.height = targetH
            this.defaultDelayMs = delayMs.coerceIn(20, 5000)
            this.frameCount = 0

            val stream = this.outputStream ?: return false

            // 1. GIF Header
            writeString(stream, "GIF89a")

            // 2. Logical Screen Descriptor
            writeShort(stream, targetW)
            writeShort(stream, targetH)
            stream.write(0x70) // No Global Color Table, 8-bit color resolution
            stream.write(0)    // Background Color Index
            stream.write(0)    // Pixel Aspect Ratio

            // 3. Netscape Application Extension for Looping
            if (repeatCount >= 0) {
                stream.write(0x21) // Extension Introducer
                stream.write(0xFF) // Application Extension Label
                stream.write(11)   // Block Size
                writeString(stream, "NETSCAPE2.0")
                stream.write(3)    // Sub-block Length
                stream.write(1)    // Sub-block ID
                writeShort(stream, repeatCount)
                stream.write(0)    // Block Terminator
            }

            // Allocate or size reusable buffers once
            val pixelCount = targetW * targetH
            if (reusablePixelBuffer == null || reusablePixelBuffer!!.size < pixelCount) {
                reusablePixelBuffer = IntArray(pixelCount)
            }
            if (reusableIndexedBuffer == null || reusableIndexedBuffer!!.size < pixelCount) {
                reusableIndexedBuffer = ByteArray(pixelCount)
            }

            isStarted = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Streams a single frame directly into the GIF output.
     * Automatically downscales input bitmap if dimensions exceed target width/height.
     */
    fun addFrame(bitmap: Bitmap, customDelayMs: Int? = null): Boolean {
        if (!isStarted || outputStream == null) return false
        val stream = outputStream ?: return false

        var scaledBitmap: Bitmap? = null
        try {
            val frameDelay = customDelayMs ?: defaultDelayMs
            val w = width
            val h = height
            val pixelCount = w * h

            // 1. Downscale/Scale input bitmap if dimensions differ
            val effectiveBitmap = if (bitmap.width != w || bitmap.height != h) {
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
                scaledBitmap
            } else {
                bitmap
            }

            val pixels = reusablePixelBuffer ?: IntArray(pixelCount).also { reusablePixelBuffer = it }
            val indexedPixels = reusableIndexedBuffer ?: ByteArray(pixelCount).also { reusableIndexedBuffer = it }

            effectiveBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            // 2. Build Fast Color Palette without boxed HashMaps
            paletteSize = 0
            val maxColors = 256

            // Direct mapping with primitive hash table
            val tableSize = 1024
            val mask = tableSize - 1
            val hashKeys = IntArray(tableSize) { -1 }
            val hashValues = IntArray(tableSize) { -1 }

            for (i in 0 until pixelCount) {
                val color = pixels[i] and 0x00FFFFFF // Discard alpha for standard GIF palette
                var hash = (color xor (color ushr 12)) and mask

                var foundIndex = -1
                while (hashKeys[hash] != -1) {
                    if (hashKeys[hash] == color) {
                        foundIndex = hashValues[hash]
                        break
                    }
                    hash = (hash + 1) and mask
                }

                if (foundIndex == -1) {
                    if (paletteSize < maxColors) {
                        val newIdx = paletteSize++
                        colorPalette[newIdx] = color
                        hashKeys[hash] = color
                        hashValues[hash] = newIdx
                        foundIndex = newIdx
                    } else {
                        // Nearest color fallback or default index 0
                        foundIndex = 0
                    }
                }

                indexedPixels[i] = foundIndex.toByte()
            }

            // Determine bit depth of local color table
            var pSize = 2
            while (pSize < paletteSize && pSize < 256) {
                pSize = pSize shl 1
            }
            var paletteBits = 1
            while ((1 shl paletteBits) < pSize) {
                paletteBits++
            }
            paletteBits = paletteBits.coerceAtLeast(1)

            // 3. Graphic Control Extension (Delay & disposal)
            stream.write(0x21) // Extension Introducer
            stream.write(0xF9) // GCE Label
            stream.write(4)    // Block Size
            stream.write(0x00) // Disposal Method: 0 (no disposal specified), User Input: 0, Transparent: 0
            writeShort(stream, max(1, frameDelay / 10)) // Delay in hundredths of a second (10ms units)
            stream.write(0)    // Transparent Color Index
            stream.write(0)    // Block Terminator

            // 4. Image Descriptor
            stream.write(0x2C) // Image Separator
            writeShort(stream, 0) // Left
            writeShort(stream, 0) // Top
            writeShort(stream, w) // Width
            writeShort(stream, h) // Height
            // Local Color Table Flag (0x80) | Table Size (paletteBits - 1)
            stream.write(0x80 or (paletteBits - 1))

            // 5. Write Local Color Table (RGB triplets)
            for (i in 0 until pSize) {
                if (i < paletteSize) {
                    val c = colorPalette[i]
                    stream.write((c shr 16) and 0xFF) // Red
                    stream.write((c shr 8) and 0xFF)  // Green
                    stream.write(c and 0xFF)         // Blue
                } else {
                    stream.write(0)
                    stream.write(0)
                    stream.write(0)
                }
            }

            // 6. LZW Encode Pixel Data directly to output stream
            val initCodeSize = max(2, paletteBits)
            lzwEncoder.encode(w, h, indexedPixels, initCodeSize, stream)

            frameCount++
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            scaledBitmap?.recycle()
        }
    }

    /**
     * Finalizes GIF file by writing trailer byte and flushing stream.
     */
    fun finish(): Boolean {
        if (!isStarted || outputStream == null) return false
        return try {
            val stream = outputStream!!
            stream.write(0x3B) // GIF Trailer (0x3B = ';')
            stream.flush()
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
     * High-speed, low-memory LZW encoder utilizing preallocated integer tables.
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
        private var clearCode = 0
        private var eofCode = 0
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

            if (code == eofCode) {
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
            freeEnt = clearCode + 2
            clearFlg = true
            output(clearCode, outs)
        }

        fun encode(
            imgW: Int,
            imgH: Int,
            pixAry: ByteArray,
            initCodeSize: Int,
            os: OutputStream
        ) {
            var curPixel = 0
            val totalPixels = imgW * imgH
            os.write(initCodeSize)
            gInitBits = initCodeSize
            clearFlg = false
            nBits = gInitBits + 1
            maxcode = maxCode(nBits)
            clearCode = 1 shl gInitBits
            eofCode = clearCode + 1
            freeEnt = clearCode + 2
            aCount = 0
            curAccum = 0
            curBits = 0

            var ent = if (curPixel < totalPixels) pixAry[curPixel++].toInt() and 0xFF else -1
            var hshift = 0
            var fcode = HSIZE
            while (fcode < 65536) {
                hshift++
                fcode *= 2
            }
            hshift = 8 - hshift

            for (i in 0 until HSIZE) htab[i] = -1

            output(clearCode, os)

            while (curPixel < totalPixels) {
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
            output(eofCode, os)
            os.write(0) // Block terminator
        }
    }
}
