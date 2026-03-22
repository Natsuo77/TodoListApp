package com.salman.nfcreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.salman.nfcreader.databinding.ActivityWriteDataBinding

class WriteData : AppCompatActivity() {
    private lateinit var binding: ActivityWriteDataBinding
    private var intentFiltersArray: Array<IntentFilter>? = null
    
    private val techListsArray = arrayOf(
        arrayOf(Ndef::class.java.name),
        arrayOf(NfcA::class.java.name),
        arrayOf(NfcF::class.java.name),
        arrayOf(NfcV::class.java.name)
    )
    
    private val nfcAdapter: NfcAdapter? by lazy {
        NfcAdapter.getDefaultAdapter(this)
    }
    private var pendingIntent: PendingIntent? = null

    // Listes pour garder une trace des champs dynamiques
    private val dynamicFieldsAllergies = mutableListOf<EditText>()
    private val dynamicFieldsMaladies = mutableListOf<EditText>()
    private val dynamicFieldsTraitements = mutableListOf<EditText>()
    private val dynamicFieldsDispositifs = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriteDataBinding.inflate(layoutInflater)
        supportActionBar?.hide()
        setContentView(binding.root)

        binding.btnValidate.setOnClickListener { finish() }

        // Ajout des premiers champs par défaut
        addField(binding.containerAllergies, "Allergie", 30, dynamicFieldsAllergies)
        addField(binding.containerMaladies, "Maladie", 50, dynamicFieldsMaladies)
        addField(binding.containerTraitements, "Traitement", 30, dynamicFieldsTraitements)
        addField(binding.containerDispositifs, "Dispositif", 50, dynamicFieldsDispositifs)

        // Configuration des boutons +
        binding.btnAddAllergie.setOnClickListener { addField(binding.containerAllergies, "Allergie", 30, dynamicFieldsAllergies) }
        binding.btnAddMaladie.setOnClickListener { addField(binding.containerMaladies, "Maladie", 50, dynamicFieldsMaladies) }
        binding.btnAddTraitement.setOnClickListener { addField(binding.containerTraitements, "Traitement", 30, dynamicFieldsTraitements) }
        binding.btnAddDispositif.setOnClickListener { addField(binding.containerDispositifs, "Dispositif", 50, dynamicFieldsDispositifs) }

        setupBaseTextWatchers()
        initNfc()
    }

    private fun addField(container: LinearLayout, hintPrefix: String, maxLength: Int, list: MutableList<EditText>) {
        val context = container.context
        val fieldLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Header du champ (Label + Compteur + Bouton supprimer)
        val header = RelativeLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val countText = TextView(context).apply {
            id = View.generateViewId()
            text = maxLength.toString()
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 10f
        }

        val label = TextView(context).apply {
            text = "$hintPrefix ${list.size + 1} ($maxLength char)"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 10f
        }

        val deleteBtn = Button(context).apply {
            text = "−"
            background = ContextCompat.getDrawable(context, R.drawable.button)
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            textSize = 14f
            setPadding(0, 0, 0, 0)
            layoutParams = RelativeLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply {
                addRule(RelativeLayout.LEFT_OF, countText.id)
                addRule(RelativeLayout.CENTER_VERTICAL)
                marginEnd = dpToPx(8)
            }
        }

        val labelParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        labelParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        labelParams.addRule(RelativeLayout.CENTER_VERTICAL)
        header.addView(label, labelParams)

        val countParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        countParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        countParams.addRule(RelativeLayout.CENTER_VERTICAL)
        header.addView(countText, countParams)
        header.addView(deleteBtn)

        fieldLayout.addView(header)

        // EditText
        val editText = EditText(context).apply {
            hint = hintPrefix
            filters = arrayOf(InputFilter.LengthFilter(maxLength))
            background = ContextCompat.getDrawable(context, R.drawable.button)
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setHintTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 0, dpToPx(8))
            layoutParams = params
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val remaining = maxLength - (s?.length ?: 0)
                countText.text = remaining.toString()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        deleteBtn.setOnClickListener {
            container.removeView(fieldLayout)
            list.remove(editText)
            updateLabels(container, hintPrefix, maxLength)
        }

        fieldLayout.addView(editText)
        container.addView(fieldLayout)
        list.add(editText)
    }

    private fun updateLabels(container: LinearLayout, hintPrefix: String, maxLength: Int) {
        for (i in 0 until container.childCount) {
            val fieldLayout = container.getChildAt(i) as LinearLayout
            val header = fieldLayout.getChildAt(0) as RelativeLayout
            val label = header.getChildAt(0) as TextView
            label.text = "$hintPrefix ${i + 1} ($maxLength char)"
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupBaseTextWatchers() {
        val pairs = listOf(
            binding.etNom to binding.tvCountNom,
            binding.etPrenom to binding.tvCountPrenom,
            binding.etDateNaissance to binding.tvCountDate,
            binding.etSexe to binding.tvCountSexe,
            binding.etAdresse to binding.tvCountAdresse,
            binding.etContactUrgence to binding.tvCountContact,
            binding.etTaille to binding.tvCountTaille,
            binding.etPoids to binding.tvCountPoids,
            binding.etGroupeSanguin to binding.tvCountSang
        )

        pairs.forEach { (editText, textView) ->
            val maxLength = editText.filters.filterIsInstance<InputFilter.LengthFilter>().firstOrNull()?.max ?: 0
            textView.text = maxLength.toString()
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    textView.text = (maxLength - (s?.length ?: 0)).toString()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    private fun initNfc() {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply { try { addDataType("*/*") } catch (e: Exception) {} }
        intentFiltersArray = arrayOf(ndef, IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
        
        if (nfcAdapter == null) {
            showDialog("NFC non supporté")
        } else if (!nfcAdapter!!.isEnabled) {
            showDialog("NFC Désactivé", true)
        }
    }

    private fun showDialog(msg: String, settings: Boolean = false) {
        val builder = AlertDialog.Builder(this, R.style.MyAlertDialogStyle).setMessage(msg)
        if (settings) builder.setPositiveButton("Paramètres") { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
        builder.setNegativeButton("OK", null).show()
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            val csvData = StringBuilder()
            // Collecte des champs fixes
            val fixedFields = listOf(binding.etNom, binding.etPrenom, binding.etDateNaissance, binding.etSexe, binding.etAdresse, binding.etContactUrgence, binding.etTaille, binding.etPoids, binding.etGroupeSanguin)
            fixedFields.forEach { csvData.append(it.text.toString().replace(";", " ")).append(";") }
            
            // Collecte des champs dynamiques (Allergies, Maladies, Traitements, Dispositifs)
            fun appendList(list: List<EditText>) {
                val content = list.map { it.text.toString().replace(";", " ") }.filter { it.isNotBlank() }.joinToString(",")
                csvData.append(content).append(";")
            }
            appendList(dynamicFieldsAllergies)
            appendList(dynamicFieldsMaladies)
            appendList(dynamicFieldsTraitements)
            appendList(dynamicFieldsDispositifs)

            val finalData = csvData.toString().removeSuffix(";")

            if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
                val ndef = Ndef.get(tag) ?: return
                if (ndef.isWritable) {
                    ndef.connect()
                    ndef.writeNdefMessage(NdefMessage(arrayOf(NdefRecord.createTextRecord("fr", finalData))))
                    ndef.close()
                    Toast.makeText(this, "Données enregistrées !", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (ex: Exception) {
            Toast.makeText(this, "Erreur: ${ex.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        nfcAdapter?.disableForegroundDispatch(this)
        super.onPause()
    }
}
