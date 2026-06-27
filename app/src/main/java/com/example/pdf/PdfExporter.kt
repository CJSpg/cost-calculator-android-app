package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import com.example.data.MealPlan
import com.example.data.MealPlanDay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    fun exportMealPlanToPdf(context: Context, plan: MealPlan, days: List<MealPlanDay>): File? {
        val pdfDocument = PdfDocument()
        
        // A4 page dimensions in points (72 points per inch)
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
        }

        val tableHeaderPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val tableContentPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }

        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#F5F5F5")
            style = Paint.Style.FILL
        }

        val dayHeaderBgPaint = Paint().apply {
            color = Color.parseColor("#E0F2F1") // Soft teal for days
            style = Paint.Style.FILL
        }

        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val startStr = dateFormat.format(Date(plan.startDate))
        val endStr = dateFormat.format(Date(plan.endDate))

        // Group 45 days into weeks of 7 days (45 days is 7 weeks: 7 * 6 weeks + 3 days in 7th week)
        val daysSorted = days.sortedBy { it.dayIndex }
        val weeks = daysSorted.chunked(7)

        for ((weekIndex, weekDays) in weeks.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, weekIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var y = 40f

            // 1. Draw Page Header
            canvas.drawText("45 天客製化菜單 - 第 ${weekIndex + 1} 週", 30f, y, titlePaint)
            y += 20f
            
            val metaText = "客戶姓名: ${plan.customerName}   |   代碼: ${plan.planCode}   |   開始日: $startStr   |   結束日: $endStr"
            canvas.drawText(metaText, 30f, y, metaPaint)
            y += 15f
            
            // Draw separating line
            canvas.drawLine(30f, y, pageWidth - 30f, y, borderPaint)
            y += 20f

            // 2. Draw Table Header
            val xDay = 30f
            val xTime = 120f
            val xMealName = 180f
            val xItems = 260f
            val xNote = 450f

            canvas.drawRect(30f, y - 12f, pageWidth - 30f, y + 6f, headerBgPaint)
            canvas.drawText("日期 & 日型", xDay + 5, y, tableHeaderPaint)
            canvas.drawText("時間", xTime, y, tableHeaderPaint)
            canvas.drawText("餐次", xMealName, y, tableHeaderPaint)
            canvas.drawText("建議品項", xItems, y, tableHeaderPaint)
            canvas.drawText("備註", xNote, y, tableHeaderPaint)
            y += 12f
            canvas.drawLine(30f, y, pageWidth - 30f, y, borderPaint)
            y += 10f

            // 3. Draw Days for the week
            for (day in weekDays) {
                val dayStr = "第 ${day.dayIndex} 天\n${dateFormat.format(Date(day.date))}\n[${day.dayTypeName}]"
                val dayLines = dayStr.split("\n")

                // We measure how tall this day row needs to be based on meals
                val meals = day.meals
                val rowStartY = y - 5f

                if (meals.isEmpty()) {
                    // Draw empty day row
                    canvas.drawRect(30f, rowStartY, pageWidth - 30f, y + 20f, dayHeaderBgPaint)
                    for ((lineIdx, line) in dayLines.withIndex()) {
                        canvas.drawText(line, xDay + 5, y + (lineIdx * 12), tableContentPaint)
                    }
                    canvas.drawText("無餐次安排", xTime, y, tableContentPaint)
                    y += 35f
                    canvas.drawLine(30f, y, pageWidth - 30f, y, borderPaint)
                    continue
                }

                // Calculate heights & draw meal rows inside day
                var mealY = y
                for ((idx, meal) in meals.withIndex()) {
                    // Meal timing & title
                    canvas.drawText(meal.time, xTime, mealY, tableContentPaint)
                    canvas.drawText(meal.title, xMealName, mealY, tableContentPaint)

                    // Draw Items stack
                    var itemY = mealY
                    for (item in meal.items) {
                        val itemStr = "${item.menuItemName} x${item.quantity}${item.unit}"
                        // Simple wrap if item is too long
                        if (itemStr.length > 30) {
                            canvas.drawText(itemStr.substring(0, 30) + "...", xItems, itemY, tableContentPaint)
                        } else {
                            canvas.drawText(itemStr, xItems, itemY, tableContentPaint)
                        }
                        if (item.note.isNotEmpty()) {
                            canvas.drawText("(${item.note})", xItems + 110f, itemY, metaPaint)
                        }
                        itemY += 12f
                    }

                    // Draw meal-level notes
                    if (meal.note.isNotEmpty()) {
                        canvas.drawText(meal.note, xNote, mealY, tableContentPaint)
                    }

                    // Advance mealY by maximum lines in items or at least 15f
                    val itemLinesHeight = (meal.items.size * 12f).coerceAtLeast(15f)
                    mealY += itemLinesHeight + 10f
                }

                // Draw the left day-index vertical column with a soft background highlight
                canvas.drawRect(30f, rowStartY, xTime - 10f, mealY - 5f, dayHeaderBgPaint)
                for ((lineIdx, line) in dayLines.withIndex()) {
                    canvas.drawText(line, xDay + 5, rowStartY + 15f + (lineIdx * 12f), tableContentPaint)
                }

                y = mealY
                canvas.drawLine(30f, y - 5f, pageWidth - 30f, y - 5f, borderPaint)
                y += 10f
            }

            // Draw full outer border
            canvas.drawRect(30f, 40f, pageWidth - 30f, pageHeight - 40f, borderPaint)

            pdfDocument.finishPage(page)
        }

        // Save PDF to documents directory
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "MealPlan_${plan.planCode}_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.flush()
            outputStream.close()
            Log.d("PdfExporter", "Successfully exported PDF to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("PdfExporter", "Failed to export PDF", e)
            pdfDocument.close()
            null
        }
    }
}
