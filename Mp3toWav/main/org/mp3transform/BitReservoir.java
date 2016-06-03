
package org.mp3transform;


public class BitReservoir {

    private static final int BUFFER_SIZE = 4096 * 8;
    private static final int BUFFER_SIZE_MASK = BUFFER_SIZE - 1;
    private int offset, bitCount, bufferIndex;
    private final int[] buffer = new int[BUFFER_SIZE];

    int getBitCount() {
        return bitCount;
    }

    int getBits(int n) {
        bitCount += n;
        int val = 0;
        int pos = bufferIndex;
        if (pos + n < BUFFER_SIZE) {
            while (n-- > 0) {
                val <<= 1;
                val |= ((buffer[pos++] != 0) ? 1 : 0);
            }
        } else {
            while (n-- > 0) {
                val <<= 1;
                val |= ((buffer[pos] != 0) ? 1 : 0);
                pos = (pos + 1) & BUFFER_SIZE_MASK;
            }
        }
        bufferIndex = pos;
        return val;
    }

    int getOneBit() {
        bitCount++;
        int val = buffer[bufferIndex];
        bufferIndex = (bufferIndex + 1) & BUFFER_SIZE_MASK;
        return val;
    }

    void putByte(int val) {
        int ofs = offset;
        buffer[ofs++] = val & 0x80;
        buffer[ofs++] = val & 0x40;
        buffer[ofs++] = val & 0x20;
        buffer[ofs++] = val & 0x10;
        buffer[ofs++] = val & 0x08;
        buffer[ofs++] = val & 0x04;
        buffer[ofs++] = val & 0x02;
        buffer[ofs++] = val & 0x01;
        if (ofs == BUFFER_SIZE) {
            offset = 0;
        } else {
            offset = ofs;
        }
    }

    void rewindBits(int n) {
        bitCount -= n;
        bufferIndex -= n;
        if (bufferIndex < 0) {
            bufferIndex += BUFFER_SIZE;
        }
    }

    void rewindBytes(int n) {
        int bits = (n << 3);
        bitCount -= bits;
        bufferIndex -= bits;
        if (bufferIndex < 0) {
            bufferIndex += BUFFER_SIZE;
        }
    }

}
