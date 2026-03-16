package com.salman.nfcreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcF
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.salman.nfcreader.databinding.ActivityWriteDataBinding

class WriteData : AppCompatActivity() {
    private lateinit var binding: ActivityWriteDataBinding
    private var intentFiltersArray: Array<IntentFilter>? = null
    private val techListsArray = arrayOf(arrayOf(NfcF::class.java.name))
    private val nfcAdapter: NfcAdapter? by lazy {
        NfcAdapter.getDefaultAdapter(this)
    }
    private var pendingIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriteDataBinding.inflate(layoutInflater)
        supportActionBar?.hide()
        setContentView(binding.root)

        binding.btnback.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        
        //nfc process start
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags
        )
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            ndef.addDataType("text/plain")
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            throw RuntimeException("fail", e)
        }
        intentFiltersArray = arrayOf(ndef)
        if (nfcAdapter == null) {
            val builder = AlertDialog.Builder(this@WriteData, R.style.MyAlertDialogStyle)
            builder.setMessage("This device doesn't support NFC.")
            builder.setPositiveButton("Cancel", null)
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
        } else if (!nfcAdapter!!.isEnabled) {
            val builder = AlertDialog.Builder(this@WriteData, R.style.MyAlertDialogStyle)
            builder.setTitle("NFC Disabled")
            builder.setMessage("Please Enable NFC")
            builder.setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            builder.setNegativeButton("Cancel", null)
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            // Construction de la chaîne CSV avec le séparateur ';'
            val csvData = StringBuilder()
            val fields = listOf(
                binding.etNom, binding.etPrenom, binding.etDateNaissance, binding.etSexe,
                binding.etAdresse, binding.etContactUrgence, binding.etTaille, binding.etPoids,
                binding.etGroupeSanguin,
                binding.etAllergie1, binding.etAllergie2, binding.etAllergie3, binding.etAllergie4,
                binding.etMaladie1, binding.etMaladie2, binding.etMaladie3, binding.etMaladie4,
                binding.etTraitement1, binding.etTraitement2, binding.etTraitement3, binding.etTraitement4,
                binding.etTraitement5, binding.etTraitement6, binding.etTraitement7, binding.etTraitement8,
                binding.etDispositif1, binding.etDispositif2
            )

            fields.forEachIndexed { index, editText ->
                // Remplacer les ';' saisis pour éviter de corrompre le CSV
                val text = editText.text.toString().replace(";", " ")
                csvData.append(text)
                if (index < fields.size - 1) {
                    csvData.append(";")
                }
            }

            val finalData = csvData.toString()

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
