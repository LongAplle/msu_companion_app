package edu.msu.cse476.msucompanion;

import androidx.room.TypeConverter;

import java.util.Date;

/**
 * Date - Long converters to allow Room to reference Date type
 */
public class DateLongConverters {
    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}
