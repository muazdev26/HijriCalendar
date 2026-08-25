package com.muazdev.hijricalendar.shared

import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.ui.HijriCalendarLabels

private val urduHijriMonths = listOf(
    "محرم", "صفر", "ربیع الاول", "ربیع الثانی",
    "جمادی الاول", "جمادی الثانی", "رجب", "شعبان",
    "رمضان", "شوال", "ذی القعدہ", "ذی الحجہ",
)

private val urduGregorianMonths = listOf(
    "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
    "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر",
)

val UrduCalendarLabels = HijriCalendarLabels(
    hijriMonthName = { _, month -> urduHijriMonths[month - 1] },
    gregorianMonthName = { month -> urduGregorianMonths[month - 1] },
    weekdayShortName = { weekDay ->
        when (weekDay) {
            WeekDay.SATURDAY -> "ہفتہ"
            WeekDay.SUNDAY -> "اتوار"
            WeekDay.MONDAY -> "پیر"
            WeekDay.TUESDAY -> "منگل"
            WeekDay.WEDNESDAY -> "بدھ"
            WeekDay.THURSDAY -> "جمعرات"
            WeekDay.FRIDAY -> "جمعہ"
        }
    },
    previousMonthContentDescription = "پچھلا مہینہ",
    nextMonthContentDescription = "اگلا مہینہ",
)
