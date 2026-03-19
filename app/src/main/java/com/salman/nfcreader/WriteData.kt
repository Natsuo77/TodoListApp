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
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.salman.nfcreader.databinding.ActivityWriteDataBinding

class WriteData : AppCompatActivity() {
    private lateinit var binding: ActivityWriteDataBinding
    private var intentFiltersArray: Array<IntentFilter>? = null
    
    // Support de plusieurs technologies pour une meilleure compatibilité
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriteDataBinding.inflate(layoutInflater)
        supportActionBar?.hide()
        setContentView(binding.root)

        // Suppression du bouton retour (btnback) car il a été enlevé du layout
        
        binding.btnValidate.setOnClickListener {
            // On ferme simplement l'activité pour revenir à l'écran de lecture
            finish()
        }

        setupTextWatchers()
        
        // Initialisation du PendingIntent pour le scan NFC
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags
        )
        
        // Configuration des filtres NFC
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        val tech = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        try {
            ndef.addDataType("*/*")
        } catch (e: Exception) { }
        
        intentFiltersArray = arrayOf(ndef, tech)
        
        if (nfcAdapter == null) {
            val builder = AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
            builder.setMessage("Cet appareil ne supporte pas le NFC.")
            builder.setPositiveButton("OK", null)
            builder.show()
        } else if (!nfcAdapter!!.isEnabled) {
            val builder = AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
            builder.setTitle("NFC Désactivé")
            builder.setMessage("Veuillez activer le NFC dans les paramètres.")
            builder.setPositiveButton("Paramètres") { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            builder.setNegativeButton("Annuler", null)
            builder.show()
        }
    }

    private fun getCsvData(): String {
        val csvData = StringBuilder()
        val fields = listOf(
            binding.etNom, binding.etPrenom, binding.etDateNaissance, binding.etSexe,
            binding.etAdresse, binding.etContactUrgence, binding.etTaille, binding.etPoids,
            binding.etGroupeSanguin, binding.etAllergie1, binding.etAllergie2,
            binding.etMaladie1, binding.etTraitement1, binding.etDispositif1
        )

        fields.forEachIndexed { index, editText ->
            val text = editText.text.toString().replace(";", " ")
            csvData.append(text)
            if (index < fields.size - 1) {
                csvData.append(";")
            }
        }
        return csvData.toString()
    }

    private fun setupTextWatchers() {
        val pairs = listOf(
            binding.etNom to binding.tvCountNom,
            binding.etPrenom to binding.tvCountPrenom,
            binding.etDateNaissance to binding.tvCountDate,
            binding.etSexe to binding.tvCountSexe,
            binding.etAdresse to binding.tvCountAdresse,
            binding.etContactUrgence to binding.tvCountContact,
            binding.etTaille to binding.tvCountTaille,
            binding.etPoids to binding.tvCountPoids,
            binding.etGroupeSanguin to binding.tvCountSang,
            binding.etAllergie1 to binding.tvCountAllergie1,
            binding.etAllergie2 to binding.tvCountAllergie2,
            binding.etMaladie1 to binding.tvCountMaladie1,
            binding.etTraitement1 to binding.tvCountTraitement1,
            binding.etDispositif1 to binding.tvCountDispositif1
        )

        pairs.forEach { (editText, textView) ->
            val maxLength = editText.filters.filterIsInstance<android.text.InputFilter.LengthFilter>().firstOrNull()?.max ?: 0
            textView.text = maxLength.toString()
            
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val remaining = maxLength - (s?.length ?: 0)
                    textView.text = remaining.toString()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            val finalData = getCsvData()

            if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
                val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                } ?: return
                
                val ndef = Ndef.get(tag) ?: return

                if (ndef.isWritable) {
                    val message = NdefMessage(
                        arrayOf(
                            NdefRecord.createTextRecord("fr", finalData)
                        )
                    )

                    ndef.connect()
                    ndef.writeNdefMessage(message)
                    ndef.close()

                    Toast.makeText(applicationContext, "Données médicales enregistrées sur le tag !", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(applicationContext, "Tag non inscriptible !", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (ex: Exception) {
            Toast.makeText(applicationContext, "Erreur: ${ex.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        nfcAdapter?.disableForegroundDispatch(this)
        super.onPause()
    }
}
