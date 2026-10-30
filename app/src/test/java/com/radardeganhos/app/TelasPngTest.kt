package com.radardeganhos.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Desenha as telas num PNG (build/screens/) e falha se saírem em branco.
 *
 * Serve para duas coisas: olhar a tela sem instalar o APK, e pegar o caso em que um
 * layout "infla" sem erro mas não desenha nada (card some, texto preto no preto,
 * altura zero). O PNG de cada execução fica em `app/build/screens/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TelasPngTest {

    private fun salvar(v: View, nome: String, largura: Int, altura: Int): File {
        val bmp = Bitmap.createBitmap(largura, altura, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val arq = File("build/screens/$nome.png")
        arq.parentFile?.mkdirs()
        FileOutputStream(arq).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Tela em branco (ou preta chapada) tem pouquíssimas cores: o card laranja,
        // os textos e os campos garantem bem mais que isso.
        val cores = mutableSetOf<Int>()
        var y = 0
        while (y < altura) {
            var x = 0
            while (x < largura) {
                cores.add(bmp.getPixel(x, y))
                x += 7
            }
            y += 7
        }
        assertTrue("$nome saiu com só ${cores.size} cores: a tela não desenhou", cores.size >= 20)
        bmp.recycle()
        return arq
    }

    private fun actividadeComPlantao(): MainActivity {
        Robolectric.buildActivity(MainActivity::class.java).setup().get().let { primeira ->
            PlantaoStore.salvar(
                primeira,
                Plantao(
                    ganho99 = 187.40,
                    ganhoUber = 254.90,
                    ganhoInDrive = 42.0,
                    ganhoGorjetas = 15.0,
                    km = 148.0,
                    inicioMin = 7 * 60 + 40,
                    aoVivo = true,
                    outros = 32.0,
                    meta = 300.0
                )
            )
        }
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    @Test
    fun homeDesenhaComOsNumerosDoDia() {
        val act = actividadeComPlantao()
        val home = act.findViewById<View>(R.id.tabHome) as android.widget.ScrollView
        val conteudo = home.getChildAt(0) as LinearLayout
        val largura = 1080
        conteudo.measure(
            View.MeasureSpec.makeMeasureSpec(largura, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        conteudo.layout(0, 0, largura, conteudo.measuredHeight)
        conteudo.setBackgroundColor(act.resources.getColor(R.color.bg, act.theme))

        val arq = salvar(conteudo, "home", largura, conteudo.measuredHeight)
        assertTrue("PNG da Home pequeno demais", arq.length() > 20_000)
    }

    @Test
    fun configDesenha() {
        val act = actividadeComPlantao()
        act.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.navConfig
        val raiz = act.window.decorView
        raiz.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY)
        )
        raiz.layout(0, 0, 1080, 2200)
        salvar(raiz, "config", 1080, 2200)
    }
}
