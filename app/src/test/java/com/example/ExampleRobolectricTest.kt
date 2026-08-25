package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.engine.CRC32Helper
import com.example.domain.engine.CsvBatchParser
import com.example.domain.model.ContentType
import com.example.domain.model.DotStyle
import com.example.domain.model.ErrorCorrection
import com.example.domain.model.QRStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun readStringFromContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("QRaft", appName)
  }

  @Test
  fun testCrc32Helper() {
    val input = "Hello QRaft Animated Transfer"
    val hex = CRC32Helper.computeHex(input)
    assertNotNull(hex)
    assertEquals(8, hex.length)
  }

  @Test
  fun testCsvBatchParser() {
    val rows = CsvBatchParser.parseCsv(CsvBatchParser.SAMPLE_CSV)
    assertTrue(rows.isNotEmpty())
    assertTrue(rows.any { it.isValid })
  }
}
