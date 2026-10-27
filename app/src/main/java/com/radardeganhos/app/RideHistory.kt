package com.radardeganhos.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Uma corrida que passou pelo app (manual ou auto-análise).
 * Guarda o que o motorista precisa conferir depois: valor, R$/km, R$/hora, o trajeto
 * e — quando a opção de print está ligada — o arquivo da captura da tela.
 */
data class RideRecord(
    val at: Long,
    val app: String?,
    val valor: Double,
    val km: Double,
    val brutoKm: Double,
    val brutoHora: Double,
    val liquido: Double,
    val origem: String?,
    val destino: String?,
    /** Arquivo do print da tela dentro de [Prints] (null quando a opção está desligada). */
    val print: String? = null
)

/**
 * Histórico local das corridas analisadas (SharedPreferences, nada sai do aparelho).
 * A serialização é texto com campos escapados, para poder ser testada sem Android.
 */
object RideHistory {
    private const val FILE = "radar_historico"
    private const val KEY = "corridas"
    private const val CAMPO = "|"
    const val MAX = 300

    /** Duas leituras do mesmo card nesse intervalo contam como uma só. */
    private const val JANELA_DEDUPE_MS = 120_000L

    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(c: Context): List<RideRecord> = decode(sp(c).getString(KEY, null).orEmpty())

    fun save(c: Context, lista: List<RideRecord>) {
        sp(c).edit().putString(KEY, encode(lista)).apply()
        // Apaga os prints das corridas que saíram do limite do histórico.
        Prints.manter(c, lista.mapNotNull { it.print }.toSet())
    }

    fun clear(c: Context) {
        sp(c).edit().remove(KEY).apply()
        Prints.limpar(c)
    }

    fun from(a: RideAnalysis, at: Long = System.currentTimeMillis()): RideRecord = RideRecord(
        at = at,
        app = a.card.app,
        valor = a.bruto,
        km = a.card.kmTotal,
        brutoKm = a.brutoKm,
        brutoHora = a.brutoHora,
        liquido = a.liquido,
        origem = a.card.origem,
        destino = a.card.destino
    )

    /**
     * Registra a corrida no topo do histórico. Devolve false quando é a mesma
     * leitura repetida (auto-análise + toque manual no mesmo card, por exemplo).
     */
    fun record(c: Context, a: RideAnalysis, at: Long = System.currentTimeMillis()): Boolean {
        val novo = from(a, at)
        val atual = load(c)
        val ultimo = atual.firstOrNull()
        if (ultimo != null && mesmoCard(ultimo, novo) && novo.at - ultimo.at in 0..JANELA_DEDUPE_MS) {
            return false
        }
        save(c, (listOf(novo) + atual).take(MAX))
        return true
    }

    /**
     * Liga o print à corrida que acabou de entrar no topo. Só deve ser chamada quando
     * [record] devolveu true — assim o arquivo fica sempre na corrida certa.
     */
    fun attachPrint(c: Context, nome: String): Boolean {
        val lista = load(c)
        val primeiro = lista.firstOrNull() ?: return false
        save(c, listOf(primeiro.copy(print = nome)) + lista.drop(1))
        return true
    }

    private fun mesmoCard(a: RideRecord, b: RideRecord): Boolean =
        a.app == b.app &&
            Math.abs(a.valor - b.valor) < 0.005 &&
            Math.abs(a.km - b.km) < 0.005 &&
            a.origem == b.origem &&
            a.destino == b.destino

    // ---- serialização -----------------------------------------------------

    private fun esc(v: String?): String = if (v == null) "" else URLEncoder.encode(v, "UTF-8")

    private fun unesc(v: String): String? =
        if (v.isEmpty()) null else runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)

    fun encode(lista: List<RideRecord>): String = lista.joinToString("\n") { r ->
        listOf(
            r.at.toString(),
            esc(r.app),
            r.valor.toString(),
            r.km.toString(),
            r.brutoKm.toString(),
            r.brutoHora.toString(),
            r.liquido.toString(),
            esc(r.origem),
            esc(r.destino),
            esc(r.print)
        ).joinToString(CAMPO)
    }

    fun decode(texto: String): List<RideRecord> {
        if (texto.isBlank()) return emptyList()
        return texto.split('\n').mapNotNull { linha ->
            val f = linha.split(CAMPO)
            if (f.size < 7) return@mapNotNull null
            val at = f[0].toLongOrNull() ?: return@mapNotNull null
            val valor = f[2].toDoubleOrNull() ?: return@mapNotNull null
            RideRecord(
                at = at,
                app = unesc(f[1]),
                valor = valor,
                km = f[3].toDoubleOrNull() ?: 0.0,
                brutoKm = f[4].toDoubleOrNull() ?: 0.0,
                brutoHora = f[5].toDoubleOrNull() ?: 0.0,
                liquido = f[6].toDoubleOrNull() ?: 0.0,
                origem = unesc(f.getOrNull(7).orEmpty()),
                destino = unesc(f.getOrNull(8).orEmpty()),
                // Histórico gravado antes da opção de print: continua legível.
                print = unesc(f.getOrNull(9).orEmpty())
            )
        }
    }
}

/**
 * Prints das corridas, salvos como JPEG no próprio aparelho (filesDir/prints).
 *
 * A captura é reduzida antes de salvar: o print serve para conferir o card depois,
 * não para nitidez de impressão — e 300 telas em resolução cheia encheriam a memória.
 * Toda gravação é feita fora da thread principal pelo chamador.
 */
object Prints {
    private const val DIR = "prints"
    private const val MAX_LARGURA = 1080
    private const val QUALIDADE = 65

    /** Evita dois prints no mesmo milissegundo (leitura manual + automática). */
    private val sequencia = java.util.concurrent.atomic.AtomicLong(0)

    private fun dir(c: Context): File = File(c.filesDir, DIR)

    fun salvar(c: Context, bmp: Bitmap): String? = try {
        val nome = "corrida_${System.currentTimeMillis()}_${sequencia.incrementAndGet()}.jpg"
        val imagem = if (bmp.width > MAX_LARGURA) {
            val razao = MAX_LARGURA.toFloat() / bmp.width
            Bitmap.createScaledBitmap(
                bmp,
                MAX_LARGURA,
                (bmp.height * razao).toInt().coerceAtLeast(1),
                true
            )
        } else bmp
        dir(c).mkdirs()
        File(dir(c), nome).outputStream().use { os ->
            imagem.compress(Bitmap.CompressFormat.JPEG, QUALIDADE, os)
        }
        if (imagem !== bmp) imagem.recycle()
        nome
    } catch (t: Throwable) {
        null
    }

    /** Imagem decodificada para exibir na tela do histórico (reduzida, se preciso). */
    fun carregar(c: Context, nome: String, larguraMax: Int = 1080): Bitmap? {
        val arquivo = File(dir(c), nome)
        if (!arquivo.exists()) return null
        return try {
            val limites = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(arquivo.absolutePath, limites)
            var amostra = 1
            while (limites.outWidth / (amostra * 2) >= larguraMax) amostra *= 2
            BitmapFactory.decodeFile(
                arquivo.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = amostra
                    // Metade da memória por pixel: o histórico carrega várias miniaturas.
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            )
        } catch (t: Throwable) {
            null
        }
    }

    /** Remove os arquivos que não pertencem mais a nenhuma corrida do histórico. */
    fun manter(c: Context, nomes: Set<String>) {
        dir(c).listFiles()?.forEach { f ->
            if (f.name !in nomes) runCatching { f.delete() }
        }
    }

    fun limpar(c: Context) {
        runCatching { dir(c).deleteRecursively() }
    }
}
