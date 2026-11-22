package com.dkanada.gramophone.database;

import androidx.annotation.NonNull;
import androidx.room.TypeConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Stores a list of server addresses as one column, one address per line. */
public class AddressConverter {
    @TypeConverter
    @NonNull
    public static String fromList(List<String> addresses) {
        return addresses == null ? "" : String.join("\n", addresses);
    }

    @TypeConverter
    @NonNull
    public static List<String> toList(String value) {
        if (value == null || value.isEmpty()) return new ArrayList<>();

        return new ArrayList<>(Arrays.asList(value.split("\n")));
    }
}
