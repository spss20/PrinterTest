package com.ssoftwares.printertest

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.dantsu.escposprinter.exceptions.EscPosBarcodeException
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosEncodingException
import com.dantsu.escposprinter.exceptions.EscPosParserException
import com.dantsu.escposprinter.textparser.PrinterTextParserImg

class MainActivity : AppCompatActivity() {
    private var usbConnection: UsbConnection? = null
    private var usbManager: UsbManager? = null
    var usbDevice: UsbDevice? = null
    private val lorem =
        "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.\n"

    private lateinit var printBtn: Button;
    private lateinit var findDevice: Button;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findUsbDevice(0x519, 0x2013)

        findDevice = findViewById(R.id.find_device);
        printBtn = findViewById(R.id.print);

        findDevice.setOnClickListener {
            findUsbDevice(0x519 , 0x2013)
        }

        printBtn.setOnClickListener(View.OnClickListener {
            if (usbDevice == null) {
                Toast.makeText(this@MainActivity, "No Usb Printer Connected", Toast.LENGTH_SHORT)
                    .show()
                return@OnClickListener
            }
            try {
                print()
            } catch (e: EscPosConnectionException) {
                Toast.makeText(
                    this@MainActivity,
                    "Print Failed " + e.localizedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: EscPosEncodingException) {
                throw RuntimeException(e)
            } catch (e: EscPosBarcodeException) {
                throw RuntimeException(e)
            } catch (e: EscPosParserException) {
                throw RuntimeException(e)
            }
        })
    }

    @Throws(
        EscPosConnectionException::class,
        EscPosEncodingException::class,
        EscPosBarcodeException::class,
        EscPosParserException::class
    )
    private fun print() {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.print_logo)
        val printer = EscPosPrinter(UsbConnection(usbManager, usbDevice), 203, 48f, 32)
        printer.printFormattedTextAndCut(
            """
                [L]
                \n\n\n
                [C]<img>${PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)}</img>
                [C]--------------------------------
                \n\nOver
                """.trimIndent()
        )
    }

    private fun findUsbDevice(vendorId: Int, productId: Int) {
        println("Finding Usb Devices");
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        for (usbDevice in usbManager!!.deviceList.values) {
            println("USB V: ${usbDevice.vendorId} P: ${usbDevice.productId}")
            if (usbDevice.vendorId == vendorId && usbDevice.productId == productId) {
                this.usbDevice = usbDevice;
                this.usbConnection = UsbConnection(usbManager , usbDevice);
            }
        }

        if (usbConnection != null && usbManager != null) {
            println("One Device Found, trying to open")
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(usbReceiver, filter)
            usbManager!!.requestPermission(usbConnection!!.device, permissionIntent)
        } else {
            println( "No Devices Found, Search Complete")
        }
    }

    private fun connectUsb() {
        println( "Finding Devices")
        usbConnection = UsbPrintersConnections.selectFirstConnected(this)
        usbManager = this.getSystemService(USB_SERVICE) as UsbManager
        if (usbConnection != null && usbManager != null) {
            println( "One Device Found, trying to open")
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(usbReceiver, filter)
            usbManager!!.requestPermission(usbConnection!!.device, permissionIntent)
        } else {
            println( "No Devices Found, Search Complete")
        }
    }

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    usbDevice =
                        intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbManager != null && usbDevice != null) {
                            Toast.makeText(
                                this@MainActivity,
                                "Printer Connected",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        usbConnection?.disconnect()
        unregisterReceiver(usbReceiver)
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    }
}