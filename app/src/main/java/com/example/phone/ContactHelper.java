package com.example.phone;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;

public class ContactHelper {

    public static class ContactItem {
        public String name;
        public String number;

        public ContactItem(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }

    public static List<ContactItem> findContactsByName(Context context, String query) {
        List<ContactItem> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        try {
            ContentResolver resolver = context.getContentResolver();
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
            String[] selectionArgs = new String[]{"%" + query.trim() + "%"};

            Cursor cursor = resolver.query(
                    uri,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    selection,
                    selectionArgs,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String number = cursor.getString(1);
                    if (number != null && !number.isEmpty()) {
                        results.add(new ContactItem(name, number));
                    }
                }
                cursor.close();
            }
        } catch (SecurityException ignored) {
            // Permission not granted
        } catch (Exception ignored) {}

        return results;
    }
}
