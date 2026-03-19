package com.salman.nfcreader

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.*
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.salman.nfcreader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var intentFiltersArray: Array<IntentFilter>? = null
    
    private val techListsArray = arrayOf(
        arrayOf(Ndef::class.java.name),
        arrayOf(NfcA::class.java.name),
        arrayOf(NfcB::class.java.name),
        arrayOf(NfcV::class.java.name),
        arrayOf(NfcF::class.java.name),
        arrayOf(IsoDep::class.java.name),
        arrayOf(MifareClassic::class.java.name),
        arrayOf(MifareUltralight::class.java.name)
    )
    
    private val nfcAdapter: NfcAdapter? by lazy {
        val nfcManager = getSystemService(Context.NFC_SERVICE) as NfcManager
        nfcManager.defaultAdapter
    }
    
    private var pendingIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        supportActionBar?.hide()
        setContentView(binding.root)

        binding.btnwrite.setOnClickListener {
            val intent = Intent(this, WriteData::class.java)
            startActivity(intent)
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags
        )
        
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        val tech = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tag = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        try {
            ndef.addDataType("*/*")
        } catch (e: Exception) { }
        
        intentFiltersArray = arrayOf(ndef, tech, tag)
        
        checkNfcSupport()
    }

    private fun checkNfcSupport() {
        if (nfcAdapter == null) {
            val builder = AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
            builder.setTitle("NFC non supporté")
            builder.setMessage("Ce terminal ne supporte pas le NFC.")
            builder.setPositiveButton("OK", null)
            builder.show()
        } else if (!nfcAdapter!!.isEnabled) {
            val builder = AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
            builder.setTitle("NFC Désactivé")
            builder.setMessage("Veuillez activer le NFC.")
            builder.setPositiveButton("Paramètres") { _, _ -> 
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) 
            }
            builder.setNegativeButton("Annuler", null)
            builder.show()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            
            val parcelables = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (parcelables != null && parcelables.isNotEmpty()) {
                try {
                    val inNdefMessage = parcelables[0] as NdefMessage
                    val payload = inNdefMessage.records[0].payload
                    
                    val textEncoding = if ((payload[0].toInt() and 128) == 0) "UTF-8" else "UTF-16"
                    val languageCodeLength = payload[0].toInt() and 63
                    val fullText = String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, charset(textEncoding))
                    
                    displayData(fullText)
                    Toast.makeText(this, "Données lues du tag !", Toast.LENGTH_SHORT).show()
                } catch (ex: Exception) {
                    Toast.makeText(this, "Erreur de lecture : ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                tag?.let { readNdefFromTag(it) }
            }
        }
    }

    private fun readNdefFromTag(tag: Tag) {
        val ndef = Ndef.get(tag)
        try {
            ndef?.let {
                it.connect()
                val ndefMessage = it.ndefMessage
                if (ndefMessage != null && ndefMessage.records.isNotEmpty()) {
                    val payload = ndefMessage.records[0].payload
                    val textEncoding = if ((payload[0].toInt() and 128) == 0) "UTF-8" else "UTF-16"
                    val languageCodeLength = payload[0].toInt() and 63
                    val fullText = String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, charset(textEncoding))
                    displayData(fullText)
                    Toast.makeText(this, "Données lues avec succès !", Toast.LENGTH_SHORT).show()
                }
                it.close()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur de lecture NDEF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayData(csvData: String) {
        val parts = csvData.split(";")
        if (parts.size >= 14) {
            binding.etNom.setText(parts[0])
            binding.etPrenom.setText(parts[1])
            binding.etDateNaissance.setText(parts[2])
            binding.etSexe.setText(parts[3])
            binding.etAdresse.setText(parts[4])
            binding.etContactUrgence.setText(parts[5])
            binding.etTaille.setText(parts[6])
            binding.etPoids.setText(parts[7])
            binding.etGroupeSanguin.setText(parts[8])
            binding.etAllergie1.setText(parts[9])
            binding.etAllergie2.setText(parts[10])
            binding.etMaladie1.setText(parts[11])
            binding.etTraitement1.setText(parts[12])
            binding.etDispositif1.setText(parts[13])
        }
    }

    override fun onPause() {
        nfcAdapter?.disableForegroundDispatch(this)
        super.onPause()
    }
}
