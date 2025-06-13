/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.derpfest.customizations.fragment

import com.android.internal.logging.nano.MetricsProto.MetricsEvent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemProperties
import android.util.Log

import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat

import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment

import org.derpfest.ui.preference.KeyboxDataPreference

class Misc : SettingsPreferenceFragment(), Preference.OnPreferenceChangeListener {
    private val TAG = "DerpFestCustomizations"
    private val REQUEST_CODE = 1001
    private val KEYBOX_DATA_PATH = "/data/misc/keybox/keybox.xml"

    private var mExpressiveDesign: SwitchPreferenceCompat? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.misc)

        mExpressiveDesign = findPreference(KEY_EXPRESSIVE_DESIGN)

        mExpressiveDesign?.apply {
            isChecked = SystemProperties.getBoolean(PROP_EXPRESSIVE_DESIGN, false)
            setOnPreferenceChangeListener(this@Misc)
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when (preference.key) {
            KEY_EXPRESSIVE_DESIGN -> {
                SystemProperties.set(PROP_EXPRESSIVE_DESIGN, if (newValue as Boolean) "1" else "0")
                return true
            }
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val xml = inputStream?.bufferedReader().use { it?.readText() }
                    if (xml != null && validateXml(xml)) {
                        Runtime.getRuntime().exec("su -c cp ${uri.path} $KEYBOX_DATA_PATH")
                        findPreference<KeyboxDataPreference>("keybox_data")?.updateSummary()
                    } else {
                        Log.e(TAG, "Invalid keybox data format")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy keybox data", e)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun validateXml(xml: String): Boolean {
        var hasEcdsaKey = false
        var hasRsaKey = false
        var hasEcdsaPrivKey = false
        var hasRsaPrivKey = false
        var ecdsaCertCount = 0
        var rsaCertCount = 0
        var numberOfKeyboxes = -1

        try {
            val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(java.io.StringReader(xml))

            var currentAlg: String? = null

            var eventType = parser.next()
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "NumberOfKeyboxes" -> {
                            parser.next() // move to TEXT event
                            if (parser.eventType == org.xmlpull.v1.XmlPullParser.TEXT) {
                                try {
                                    numberOfKeyboxes = parser.text.trim().toInt()
                                } catch (e: NumberFormatException) {
                                    numberOfKeyboxes = -1
                                }
                            }
                        }
                        "Key" -> {
                            currentAlg = parser.getAttributeValue(null, "algorithm")
                            when (currentAlg?.lowercase()) {
                                "ecdsa" -> hasEcdsaKey = true
                                "rsa" -> hasRsaKey = true
                                else -> currentAlg = null
                            }
                        }
                        "PrivateKey" -> {
                            val format = parser.getAttributeValue(null, "format")
                            if (format?.lowercase() != "pem") {
                                Log.w(TAG, "Invalid or missing format for PrivateKey")
                                return false
                            }
                            when (currentAlg?.lowercase()) {
                                "ecdsa" -> hasEcdsaPrivKey = true
                                "rsa" -> hasRsaPrivKey = true
                            }
                        }
                        "Certificate" -> {
                            val format = parser.getAttributeValue(null, "format")
                            if (format?.lowercase() != "pem") {
                                Log.w(TAG, "Invalid or missing format for Certificate")
                                return false
                            }
                            when (currentAlg?.lowercase()) {
                                "ecdsa" -> ecdsaCertCount++
                                "rsa" -> rsaCertCount++
                            }
                        }
                    }
                } else if (eventType == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "Key") {
                    currentAlg = null
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "XML validation failed", e)
            return false
        }

        return numberOfKeyboxes == 1 &&
                hasEcdsaKey && hasEcdsaPrivKey && ecdsaCertCount == 3 &&
                hasRsaKey && hasRsaPrivKey && rsaCertCount == 3
    }

    override fun getMetricsCategory(): Int = MetricsEvent.DERPFEST

    companion object {
        const val TAG = "DerpFestCustomizations"
        private const val KEY_EXPRESSIVE_DESIGN = "expressive_design"
        private const val PROP_EXPRESSIVE_DESIGN = "persist.sys.is_expressive_design_enabled"
    }
}
