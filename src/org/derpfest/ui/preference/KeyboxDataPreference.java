package org.derpfest.ui.preference;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;

public class KeyboxDataPreference extends Preference {
    private static final String TAG = "KeyboxDataPreference";
    private static final int REQUEST_CODE = 1001;
    private static final String KEYBOX_DATA_PATH = "/data/misc/keybox/keybox.xml";

    public KeyboxDataPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.keybox_data_pref);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView title = holder.itemView.findViewById(R.id.title);
        TextView summary = holder.itemView.findViewById(R.id.summary);
        ImageButton deleteButton = holder.itemView.findViewById(R.id.delete_button);

        title.setText(R.string.keybox_data_title);
        summary.setText(R.string.keybox_data_summary);

        deleteButton.setOnClickListener(v -> {
            // Reset keybox data
            try {
                Runtime.getRuntime().exec("su -c rm " + KEYBOX_DATA_PATH);
                updateSummary();
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete keybox data", e);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/xml");
            getContext().startActivity(intent);
        });

        updateSummary();
    }

    private void updateSummary() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(KEYBOX_DATA_PATH));
            StringBuilder xml = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                xml.append(line).append("\n");
            }
            reader.close();

            if (validateXml(xml.toString())) {
                setSummary(R.string.keybox_data_summary);
            } else {
                setSummary("Invalid keybox data");
            }
        } catch (Exception e) {
            setSummary("No keybox data");
        }
    }

    private boolean validateXml(String xml) {
        boolean hasEcdsaKey = false, hasRsaKey = false;
        boolean hasEcdsaPrivKey = false, hasRsaPrivKey = false;
        int ecdsaCertCount = 0, rsaCertCount = 0;
        int numberOfKeyboxes = -1;

        try {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(new StringReader(xml));

            String currentAlg = null;

            for (int eventType = parser.next(); eventType != XmlPullParser.END_DOCUMENT; eventType = parser.next()) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    switch (name) {
                        case "NumberOfKeyboxes":
                            parser.next(); // move to TEXT event
                            if (parser.getEventType() == XmlPullParser.TEXT) {
                                try {
                                    numberOfKeyboxes = Integer.parseInt(parser.getText().trim());
                                } catch (NumberFormatException e) {
                                    numberOfKeyboxes = -1;
                                }
                            }
                            break;

                        case "Key":
                            currentAlg = parser.getAttributeValue(null, "algorithm");
                            if ("ecdsa".equalsIgnoreCase(currentAlg)) {
                                hasEcdsaKey = true;
                            } else if ("rsa".equalsIgnoreCase(currentAlg)) {
                                hasRsaKey = true;
                            } else {
                                currentAlg = null; // unsupported key
                            }
                            break;

                        case "PrivateKey": {
                            String format = parser.getAttributeValue(null, "format");
                            if (!"pem".equalsIgnoreCase(format)) {
                                Log.w(TAG, "Invalid or missing format for PrivateKey");
                                return false;
                            }
                            if ("ecdsa".equalsIgnoreCase(currentAlg)) {
                                hasEcdsaPrivKey = true;
                            } else if ("rsa".equalsIgnoreCase(currentAlg)) {
                                hasRsaPrivKey = true;
                            }
                            break;
                        }

                        case "Certificate": {
                            String format = parser.getAttributeValue(null, "format");
                            if (!"pem".equalsIgnoreCase(format)) {
                                Log.w(TAG, "Invalid or missing format for Certificate");
                                return false;
                            }

                            if ("ecdsa".equalsIgnoreCase(currentAlg)) {
                                ecdsaCertCount++;
                            } else if ("rsa".equalsIgnoreCase(currentAlg)) {
                                rsaCertCount++;
                            }
                            break;
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG && "Key".equals(parser.getName())) {
                    currentAlg = null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "XML validation failed", e);
            return false;
        }

        return numberOfKeyboxes == 1
                && hasEcdsaKey && hasEcdsaPrivKey && ecdsaCertCount == 3
                && hasRsaKey && hasRsaPrivKey && rsaCertCount == 3;
    }
} 