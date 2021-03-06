package io.indexr.segment;

import org.apache.spark.unsafe.types.UTF8String;

import java.io.IOException;

import io.indexr.data.LikePattern;
import io.indexr.segment.pack.DataPack;
import io.indexr.segment.pack.DataPackNode;

public class ConstColumn implements Column {
    private String name;
    private SQLType sqlType;
    private int packCount;
    private long rowCount;
    private long numValue;
    private UTF8String strValue;
    private DataPackNode dpn;
    private RSIndex index;
    private PackExtIndex extIndex;

    public ConstColumn(int version, String name, SQLType sqlType, long rowCount, long numValue, UTF8String strValue) {
        this.name = name;
        this.sqlType = sqlType;
        this.rowCount = rowCount;
        this.packCount = DataPack.rowCountToPackCount(rowCount);
        this.numValue = numValue;
        this.strValue = strValue;

        this.dpn = new DataPackNode(version);
        dpn.setMinValue(numValue);
        dpn.setMaxValue(numValue);

        if (ColumnType.isNumber(sqlType.dataType)) {
            this.index = new RSIndexNum() {
                boolean isFloat = ColumnType.isFloatPoint(sqlType.dataType);

                @Override
                public byte isValue(int packId, long minVal, long maxVal, long packMin, long packMax) {
                    return RSIndexNum.minMaxCheck(minVal, maxVal, packMin, packMax, isFloat);
                }
            };
            this.extIndex = new PackExtIndexNum() {
                @Override
                public int serializedSize() {return 0;}

                @Override
                public void putValue(int rowId, long val) {}

                @Override
                public byte isValue(int rowId, long val) {
                    return numValue == val ? RSValue.All : RSValue.None;
                }
            };
        } else {
            this.index = new RSIndexStr() {
                @Override
                public byte isValue(int packId, UTF8String value) {
                    if (value == null ? strValue == null : value.equals(strValue)) {
                        return RSValue.All;
                    } else {
                        return RSValue.None;
                    }
                }

                @Override
                public byte isLike(int packId, LikePattern value) {
                    // TODO implementation.
                    return RSValue.Some;
                }
            };
            this.extIndex = new PackExtIndexStr() {

                @Override
                public int serializedSize() {return 0;}

                @Override
                public void putValue(int rowId, UTF8String value) {}

                @Override
                public byte isValue(int rowId, UTF8String value) {
                    if (value == null ? strValue == null : value.equals(strValue)) {
                        return RSValue.All;
                    } else {
                        return RSValue.None;
                    }
                }

                @Override
                public byte isLike(int rowId, LikePattern pattern) {
                    return RSValue.Some;
                }
            };
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public SQLType sqlType() {
        return sqlType;
    }

    @Override
    public int packCount() {
        return packCount;
    }

    @Override
    public long rowCount() throws IOException {
        return rowCount;
    }

    @Override
    public DataPackNode dpn(int packId) throws IOException {
        return dpn;
    }

    @Override
    public DPValues pack(int packId) throws IOException {
        return new DPValues() {
            @Override
            public int count() {
                return DataPack.packRowCount(rowCount, packId);
            }

            @Override
            public long uniformValAt(int index, byte type) {
                return numValue;
            }

            @Override
            public int intValueAt(int index) {
                return (int) numValue;
            }

            @Override
            public long longValueAt(int index) {
                return numValue;
            }

            @Override
            public float floatValueAt(int index) {
                return (float) Double.longBitsToDouble(numValue);
            }

            @Override
            public double doubleValueAt(int index) {
                return Double.longBitsToDouble(numValue);
            }

            @Override
            public UTF8String stringValueAt(int index) {
                return strValue;
            }
        };
    }

    @Override
    public <T extends RSIndex> T rsIndex() throws IOException {
        return (T) index;
    }

    @Override
    public <T extends PackExtIndex> T extIndex(int packId) throws IOException {
        return (T) extIndex;
    }
}
