package com.example.fishspot.feature.fish

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.ArFragment
import kotlin.math.sqrt

class ArMeasureActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private lateinit var tvDistance: TextView
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var btnPonovo: Button

    // Lista u kojoj čuvamo postavljene tačke (max 2)
    private val anchors = mutableListOf<Anchor>()
    private val markeri = mutableListOf<AnchorNode>()
    private var izmerenaDuzinaCm: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dinamički kreiramo Layout (kako ne bismo morali da pravimo poseban XML fajl)
        val rootLayout = FrameLayout(this).apply {
            id = android.view.View.generateViewId() // <-- OVA LINIJA JE FALILA
        }
        setContentView(rootLayout)

        // Dodajemo AR Fragment
        arFragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            ArFragment::class.java.name
        ) as ArFragment

        // commitNow: AR prikaz se doda odmah, pre dugmadi. Sa commit() bi se dodao kasnije,
        // preko dugmadi, pa dugme "Poništi" ne bi primalo dodir.
        supportFragmentManager.beginTransaction()
            .replace(rootLayout.id, arFragment)
            .commitNow()

        // Podesavamo UI elemente preko ekrana
        tvDistance = TextView(this).apply {
            text = "Tapni na glavu ribe"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 40f
                setColor(android.graphics.Color.parseColor("#D9132218"))
            }
            setPadding(32, 32, 32, 32)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 64
            }
        }

        btnSave = Button(this).apply {
            text = "Sačuvaj merenje"
            setTextColor(android.graphics.Color.WHITE)
            isAllCaps = false // Sprečava da tekst bude automatski velikim slovima
            textSize = 16f
            setPadding(64, 32, 64, 32) // Povećavamo dugme da bude lakše za klik

            // Pravimo dizajn za UKLJUČENO dugme (Zelena boja)
            val enabledBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 50f // Zaobljene ivice
                setColor(android.graphics.Color.parseColor("#2ECC71"))
            }

            // Pravimo dizajn za ISKLJUČENO dugme (Siva boja)
            val disabledBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 50f
                setColor(android.graphics.Color.parseColor("#1B3028"))
            }

            // Spajamo oba dizajna zavisno od 'isEnabled' stanja
            val stateList = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_enabled), enabledBg)
                addState(intArrayOf(-android.R.attr.state_enabled), disabledBg)
            }

            background = stateList
            elevation = 8f // Dodaje blagu senku
            isEnabled = false

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = 64
                marginEnd = 64
            }
            setOnClickListener {
                vratiRezultat()
            }
        }

        btnReset = Button(this).apply {
            text = "Poništi"
            setTextColor(android.graphics.Color.parseColor("#E74C3C"))
            isAllCaps = false
            textSize = 16f
            setPadding(64, 32, 64, 32)

            // Dizajn za dugme (Crvena boja za odustajanje)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 50f
                setColor(android.graphics.Color.parseColor("#E6132218"))
                setStroke(3, android.graphics.Color.parseColor("#E74C3C"))
            }
            elevation = 8f // Senka

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                bottomMargin = 64
                marginStart = 64
            }
            // Poništi: zatvara kameru i vraća na formu bez merenja
            setOnClickListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

        // Ponovo izmeri: briše postavljene tačke (vidi se tek kad postoji bar jedna tačka)
        btnPonovo = Button(this).apply {
            text = "↺ Ponovo izmeri"
            setTextColor(android.graphics.Color.WHITE)
            isAllCaps = false
            textSize = 15f
            setPadding(48, 24, 48, 24)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 50f
                setColor(android.graphics.Color.parseColor("#E6132218"))
                setStroke(3, android.graphics.Color.parseColor("#2ECC71"))
            }
            visibility = android.view.View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 220
            }
            setOnClickListener { resetujMerenje() }
        }

        listOf(tvDistance, btnSave, btnReset, btnPonovo).forEach {
            it.translationZ = 16f
            rootLayout.addView(it)
        }

        postaviArListenere()
    }

    private fun postaviArListenere() {
        arFragment.setOnTapArPlaneListener { hitResult: HitResult, _, _ ->
            if (anchors.size >= 2) {
                Toast.makeText(this, "Već su postavljene 2 tačke. Pritisni \"Ponovo izmeri\".", Toast.LENGTH_SHORT).show()
                return@setOnTapArPlaneListener
            }

            // Kreiraj sidro (Anchor) na mestu dodira
            val anchor = hitResult.createAnchor()
            anchors.add(anchor)

            // Postavi vizuelnu sferu (lopticu) na to mesto
            postaviMarker(anchor)
            btnPonovo.visibility = android.view.View.VISIBLE

            if (anchors.size == 1) {
                tvDistance.text = "Sada tapni na rep ribe"
            } else if (anchors.size == 2) {
                izracunajUdaljenost()
            }
        }
    }

    private fun postaviMarker(anchor: Anchor) {
        MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.RED))
            .thenAccept { material ->
                // Pravimo malu crvenu sferu radijusa 2 centimetra
                val sphere = ShapeFactory.makeSphere(0.01f, Vector3.zero(), material)
                val anchorNode = AnchorNode(anchor)
                anchorNode.renderable = sphere
                anchorNode.setParent(arFragment.arSceneView.scene)
                // Ako je u međuvremenu pritisnuto Poništi, ovaj marker više ne treba
                if (anchor in anchors) markeri.add(anchorNode) else anchorNode.setParent(null)
            }
    }

    private fun izracunajUdaljenost() {
        val pose1 = anchors[0].pose
        val pose2 = anchors[1].pose

        // Računanje razlike po x, y, z koordinatama
        val dx = pose1.tx() - pose2.tx()
        val dy = pose1.ty() - pose2.ty()
        val dz = pose1.tz() - pose2.tz()

        // Distanca u metrima
        val distanceMeters = sqrt((dx * dx + dy * dy + dz * dz).toDouble())

        // Pretvori u centimetre
        izmerenaDuzinaCm = distanceMeters * 100.0

        tvDistance.text = String.format("Dužina: %.1f cm", izmerenaDuzinaCm)
        btnSave.isEnabled = true
    }

    private fun resetujMerenje() {
        // Uklanjamo loptice sa scene i sidra iz ARCore-a
        markeri.forEach {
            it.renderable = null
            it.setParent(null)
        }
        markeri.clear()
        anchors.forEach { it.detach() }
        anchors.clear()

        izmerenaDuzinaCm = 0.0
        tvDistance.text = "Tapni na glavu ribe"
        btnSave.isEnabled = false
        btnPonovo.visibility = android.view.View.GONE
    }

    private fun vratiRezultat() {
        val resultIntent = Intent().apply {
            // Tvoj kod u Compose-u očekuje parametar "DUZINA"
            putExtra("DUZINA", izmerenaDuzinaCm)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish() // Zatvara AR Activity i vraća se nazad
    }
}