package com.salman.nfcreader

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcF
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.salman.nfcreader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var intentFiltersArray: Array<IntentFilter>? = null
    private val techListsArray = arrayOf(arrayOf(NfcF::class.java.name))
    
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

        try {

            binding.btnwrite.setOnClickListener {
                val intent = Intent(this, WriteData::class.java)
                startActivity(intent)
            }
            
            // Initialisation du PendingIntent pour le Foreground Dispatch
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
            
            checkNfcSupport()
        }
        catch (ex:Exception)
        {
            Toast.makeText(applicationContext, ex.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNfcSupport() {
        if (nfcAdapter == null) {
            val builder = AlertDialog.Builder(this@MainActivity, R.style.MyAlertDialogStyle)
            builder.setTitle("NFC non supporté")
            builder.setMessage("Ce terminal ne semble pas supporter le NFC ou il est désactivé par le système.")
            builder.setPositiveButton("OK", null)
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
            
            binding.txtviewshopid.text = "NFC NON SUPPORTÉ SUR CET APPAREIL !"
            binding.txtviewmachineid.visibility = View.INVISIBLE
        } else if (!nfcAdapter!!.isEnabled) {
            val builder = AlertDialog.Builder(this@MainActivity, R.style.MyAlertDialogStyle)
            builder.setTitle("NFC Désactivé")
            builder.setMessage("Veuillez activer le NFC dans les réglages.")
            binding.txtviewshopid.text = "NFC DÉSACTIVÉ. ACTIVEZ-LE DANS LES PARAMÈTRES."
            binding.txtviewmachineid.visibility = View.INVISIBLE

            builder.setPositiveButton("Paramètres") { _, _ -> 
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) 
            }
            builder.setNegativeButton("Annuler", null)
            val myDialog = builder.create()
            myDialog.setCanceledOnTouchOutside(false)
            myDialog.show()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    var machineid=""
    var shopid=""
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val action = intent.action
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action || NfcAdapter.ACTION_TECH_DISCOVERED == action) {

            val parcelables = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (parcelables != null) {
                try {
                    val inNdefMessage = parcelables[0] as NdefMessage
                    val inNdefRecords = inNdefMessage.records
                    
                    if (inNdefRecords.isNotEmpty()) {
                        val ndefRecord_0 = inNdefRecords[0]
                        val inMessage = String(ndefRecord_0.payload)
                        shopid = if (inMessage.length > 3) inMessage.drop(3) else inMessage
                        binding.txtviewshopid.text = "SHOP ID: $shopid"
                    }

                    if (inNdefRecords.size > 1) {
                        val ndefRecord_1 = inNdefRecords[1]
                        val inMessage = String(ndefRecord_1.payload)
                        machineid = if (inMessage.length > 3) inMessage.drop(3) else inMessage
                        binding.txtviewmachineid.text = "MACHINE ID: $machineid"
                    }

                    // Logique d'écriture si un User ID est saisi
                    if (binding.txtuserid.text.toString().isNotEmpty()) {
                        writeToTag(intent)
                    } else if (inNdefRecords.size > 2) {
                        val ndefRecord_2 = inNdefRecords[2]
                        val inMessage = String(ndefRecord_2.payload)
                        binding.txtviewuserid.text = "USER ID: " + if (inMessage.length > 3) inMessage.drop(3) else inMessage
                    }
                    
                } catch (ex: Exception) {
                    Toast.makeText(applicationContext, "Erreur de lecture : ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun writeToTag(intent: Intent) {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return
        
        val ndef = Ndef.get(tag) ?: return

        try {
            if (ndef.isWritable) {
                val message = NdefMessage(
                    arrayOf(
                        NdefRecord.createTextRecord("en", shopid),
                        NdefRecord.createTextRecord("en", machineid),
                        NdefRecord.createTextRecord("en", binding.txtuserid.text.toString())
                    )
                )

                ndef.connect()
                ndef.writeNdefMessage(message)
                ndef.close()

                binding.txtviewuserid.text = "USER ID: " + binding.txtuserid.text.toString()
                Toast.makeText(applicationContext, "Écriture réussie !", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Erreur d'écriture : ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        nfcAdapter?.disableForegroundDispatch(this)
        super.onPause()
    }
}
