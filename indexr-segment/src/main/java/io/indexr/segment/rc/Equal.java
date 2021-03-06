package io.indexr.segment.rc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.spark.unsafe.array.ByteArrayMethods;
import org.apache.spark.unsafe.types.UTF8String;

import java.io.IOException;
import java.util.BitSet;

import io.indexr.data.BytePiece;
import io.indexr.segment.Column;
import io.indexr.segment.ColumnType;
import io.indexr.segment.InfoSegment;
import io.indexr.segment.PackExtIndexStr;
import io.indexr.segment.RSValue;
import io.indexr.segment.Segment;
import io.indexr.segment.pack.ColumnNode;
import io.indexr.segment.pack.DataPack;

public class Equal extends ColCmpVal {
    @JsonCreator
    public Equal(@JsonProperty("attr") Attr attr,
                 @JsonProperty("numValue") long numValue,
                 @JsonProperty("strValue") String strValue) {
        super(attr, numValue, strValue);
    }

    public Equal(Attr attr,
                 long numValue,
                 UTF8String strValue) {
        super(attr, numValue, strValue);
    }

    @Override
    public String getType() {
        return "equal";
    }

    @Override
    public RCOperator applyNot() {
        return new NotEqual(attr, numValue, strValue);
    }

    @Override
    public byte roughCheckOnPack(Segment segment, int packId) throws IOException {
        assert attr.checkCurrent(segment.schema().columns);

        int colId = attr.columnId();
        Column column = segment.column(colId);
        byte type = column.dataType();
        if (ColumnType.isNumber(type)) {
            return RoughCheck_N.equalCheckOnPack(column, packId, numValue);
        } else {
            return RoughCheck_R.equalCheckOnPack(column, packId, strValue);
        }
    }

    @Override
    public byte roughCheckOnColumn(InfoSegment segment) throws IOException {
        assert attr.checkCurrent(segment.schema().columns);

        int colId = attr.columnId();
        ColumnNode columnNode = segment.columnNode(colId);
        byte type = attr.dataType();
        if (ColumnType.isNumber(type)) {
            return RoughCheck_N.equalCheckOnColumn(columnNode, type, numValue);
        } else {
            return RSValue.Some;
        }
    }

    @Override
    public byte roughCheckOnRow(Segment segment, int packId) throws IOException {
        Column column = segment.column(attr.columnId());
        byte type = attr.dataType();
        int rowCount = column.dpn(packId).objCount();
        int hitCount = 0;
        switch (type) {
            case ColumnType.STRING: {
                PackExtIndexStr extIndex = column.extIndex(packId);
                byte res = RSValue.None;
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    res = extIndex.isValue(rowId, strValue);
                    if (res != RSValue.None) {
                        break;
                    }
                }
                if (res == RSValue.None) {
                    return RSValue.None;
                }

                DataPack pack = column.pack(packId);
                BytePiece bp = new BytePiece();
                Object valBase = strValue.getBaseObject();
                long valOffset = strValue.getBaseOffset();
                int valLen = strValue.numBytes();
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    pack.rawValueAt(rowId, bp);
                    if (bp.len == valLen && ByteArrayMethods.arrayEquals(valBase, valOffset, bp.base, bp.addr, valLen)) {
                        hitCount++;
                        break;
                    }
                }
                break;
            }
            case ColumnType.INT: {
                DataPack pack = column.pack(packId);
                int value = (int) numValue;
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    int v = pack.intValueAt(rowId);
                    if (v == value) {
                        hitCount++;
                        break;
                    }
                }
                break;
            }
            case ColumnType.LONG: {
                DataPack pack = column.pack(packId);
                long value = numValue;
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    long v = pack.longValueAt(rowId);
                    if (v == value) {
                        hitCount++;
                        break;
                    }
                }
                break;
            }
            case ColumnType.FLOAT: {
                DataPack pack = column.pack(packId);
                float value = (float) Double.longBitsToDouble(numValue);
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    float v = pack.floatValueAt(rowId);
                    if (v == value) {
                        hitCount++;
                        break;
                    }
                }
                break;
            }
            case ColumnType.DOUBLE: {
                DataPack pack = column.pack(packId);
                double value = Double.longBitsToDouble(numValue);
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    double v = pack.doubleValueAt(rowId);
                    if (v == value) {
                        hitCount++;
                        break;
                    }
                }
                break;
            }
            default:
                throw new IllegalStateException("column type " + attr.dataType() + " is illegal in " + getType().toUpperCase());
        }
        if (hitCount == rowCount) {
            return RSValue.All;
        } else if (hitCount > 0) {
            return RSValue.Some;
        } else {
            return RSValue.None;
        }
    }

    @Override
    public BitSet exactCheckOnRow(Segment segment, int packId) throws IOException {
        Column column = segment.column(attr.columnId());
        DataPack pack = column.pack(packId);
        int rowCount = pack.objCount();
        BitSet colRes = new BitSet(rowCount);
        byte type = attr.dataType();
        switch (type) {
            case ColumnType.STRING: {
                BytePiece bp = new BytePiece();
                Object valBase = strValue.getBaseObject();
                long valOffset = strValue.getBaseOffset();
                int valLen = strValue.numBytes();
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    pack.rawValueAt(rowId, bp);
                    colRes.set(rowId, bp.len == valLen && ByteArrayMethods.arrayEquals(valBase, valOffset, bp.base, bp.addr, valLen));
                }
                break;
            }

            case ColumnType.INT: {
                int value = (int) numValue;
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    int v = pack.intValueAt(rowId);
                    colRes.set(rowId, v == value);
                }
                break;
            }
            case ColumnType.LONG: {
                long value = numValue;
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    long v = pack.longValueAt(rowId);
                    colRes.set(rowId, v == value);
                }
                break;
            }
            case ColumnType.FLOAT: {
                float value = (float) Double.longBitsToDouble(numValue);
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    float v = pack.floatValueAt(rowId);
                    colRes.set(rowId, v == value);
                }
                break;
            }
            case ColumnType.DOUBLE: {
                double value = Double.longBitsToDouble(numValue);
                for (int rowId = 0; rowId < rowCount; rowId++) {
                    double v = pack.doubleValueAt(rowId);
                    colRes.set(rowId, v == value);
                }
                break;
            }
            default:
                throw new IllegalStateException("column type " + attr.dataType() + " is illegal in " + getType().toUpperCase());
        }
        return colRes;
    }
}
