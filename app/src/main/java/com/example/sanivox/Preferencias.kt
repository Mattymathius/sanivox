package com.example.sanivox

import android.content.Context

fun guardarPreferencia(context: Context, clave: String, valor: Boolean) {
    val prefs = context.getSharedPreferences("configuracion", Context.MODE_PRIVATE)
    prefs.edit().putBoolean(clave, valor).apply()
}

fun obtenerPreferencia(context: Context, clave: String, porDefecto: Boolean = false): Boolean {
    val prefs = context.getSharedPreferences("configuracion", Context.MODE_PRIVATE)
    return prefs.getBoolean(clave, porDefecto)
}

fun guardarString(context: Context, clave: String, valor: String) {
    val prefs = context.getSharedPreferences("configuracion", Context.MODE_PRIVATE)
    prefs.edit().putString(clave, valor).apply()
}

fun obtenerString(context: Context, clave: String, porDefecto: String = ""): String {
    val prefs = context.getSharedPreferences("configuracion", Context.MODE_PRIVATE)
    return prefs.getString(clave, porDefecto) ?: porDefecto
}
