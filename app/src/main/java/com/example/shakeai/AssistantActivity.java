package com.example.shakeai;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.util.Log;
import java.util.ArrayList;

public class AssistantActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your command...");
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000);

        try {
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            Log.e("ShakeAI", "Speech error: " + e.getMessage());
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d("ShakeAI", "onActivityResult: " + requestCode + " / " + resultCode);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {

            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (results != null && !results.isEmpty()) {

                String command = results.get(0).toLowerCase();
                Log.d("ShakeAI", "Command: " + command);

                if (command.contains("whatsapp")) {
                    handleWhatsApp(command);
                }
            }
        }

        finish();
    }

    private void handleWhatsApp(String command) {

        String phoneNumber = null;
        String contactName = null;
        String message     = "";

        // Extract message after "type"
        if (command.contains("type")) {
            int typeIndex = command.indexOf("type");
            message = command.substring(typeIndex + 4).trim();
            Log.d("ShakeAI", "Message: " + message);
        }

        // Search contacts for name mentioned in command
        try {
            Cursor cursor = getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    new String[]{
                            ContactsContract.Contacts.DISPLAY_NAME,
                            ContactsContract.Contacts._ID
                    },
                    null, null, null
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    if (name != null && command.contains(name.toLowerCase())) {
                        contactName = name;
                        String contactId = cursor.getString(1);

                        // Get phone number for this contact
                        Cursor phoneCursor = getContentResolver().query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                new String[]{
                                        ContactsContract.CommonDataKinds.Phone.NUMBER
                                },
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                                        + " = ?",
                                new String[]{contactId},
                                null
                        );

                        if (phoneCursor != null && phoneCursor.moveToFirst()) {
                            phoneNumber = phoneCursor.getString(0)
                                    .replaceAll("[^0-9]", "");
                            // Add country code if not present
                            if (phoneNumber.length() == 10) {
                                phoneNumber = "91" + phoneNumber;
                            }
                            phoneCursor.close();
                            Log.d("ShakeAI", "Found contact: "
                                    + contactName + " - " + phoneNumber);
                            break;
                        }
                        if (phoneCursor != null) phoneCursor.close();
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("ShakeAI", "Contacts error: " + e.getMessage());
        }

        // Launch WhatsApp
        try {
            if (phoneNumber != null) {
                String url = "https://wa.me/" + phoneNumber;
                if (!message.isEmpty()) {
                    url += "?text=" + Uri.encode(message);
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d("ShakeAI", "WhatsApp opened for: " + contactName);
            } else {
                // No contact found — just open WhatsApp
                Intent intent =
                        getPackageManager().getLaunchIntentForPackage("com.whatsapp");
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Log.d("ShakeAI", "WhatsApp opened");
                }
            }
        } catch (Exception e) {
            Log.e("ShakeAI", "WhatsApp launch error: " + e.getMessage());
        }
    }
}