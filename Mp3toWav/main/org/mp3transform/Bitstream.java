package org.mp3transform;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

public final class Bitstream {
 
    static final byte INITIAL_SYNC = 0;

    static final byte STRICT_SYNC = 1;

    private static final int BUFFER_INT_SIZE = 433;

    private final int[] frameBuffer = new int[BUFFER_INT_SIZE];

    private int frameSize;

    private final byte[] frameBytes = new byte[BUFFER_INT_SIZE * 4];
    private int wordPointer;

    private int bitIndex;
    private int syncWord;
    private boolean singleChMode;
    private static final int[] BITMASK = {
            0, // dummy
            0x00000001, 0x00000003, 0x00000007, 0x0000000F, 0x0000001F, 0x0000003F, 0x0000007F, 0x000000FF, 0x000001FF, 0x000003FF, 0x000007FF, 0x00000FFF, 0x00001FFF, 0x00003FFF,
            0x00007FFF, 0x0000FFFF, 0x0001FFFF };
    private final PushbackInputStream source;
    private final Header header = new Header();
    private final byte[] syncBuffer = new byte[4];
    private byte[] rawID3v2 = null;
    private boolean firstFrame = true;

    public Bitstream(InputStream in) {
        source = new PushbackInputStream(in, BUFFER_INT_SIZE * 4);
        loadID3v2();
        closeFrame();
    }

    private void loadID3v2() {
        int size = -1;
        try {
            source.mark(10);
            size = readID3v2Header();
        } catch (IOException e) {
        } finally {
            try {
                source.reset();
            } catch (IOException e) {
            }
        }
        try {
            if (size > 0) {
                rawID3v2 = new byte[size];
                readBytes(rawID3v2, 0, rawID3v2.length);
            }
        } catch (IOException e) {
        }
    }


    private int readID3v2Header() throws IOException {
        byte[] buff = new byte[4];
        int size = -10;
        readBytes(buff, 0, 3);
        if (buff[0] == 'I' && buff[1] == 'D' && buff[2] == '3') {
            readBytes(buff, 0, 3);
            readBytes(buff, 0, 4);
            size = (buff[0] << 21) + (buff[1] << 14) + (buff[2] << 7) + buff[3];
        }
        return size + 10;
    }

    public Header readFrame() throws IOException {
        try {
            Header result = readNextFrame();
            if (firstFrame) {
                result.parseVBR(frameBytes);
                firstFrame = false;
            }
            return result;
        } catch (EOFException e) {
            return null;
        }
    }

    private Header readNextFrame() throws IOException {
        if (frameSize == -1) {
            while (true) {
                boolean ok = header.readHeader(this);
                if (ok) {
                    break;
                }
                closeFrame();
            }
        }
        return header;
    }

    void unreadFrame() throws IOException {
        if (wordPointer == -1 && bitIndex == -1 && frameSize > 0) {
            source.unread(frameBytes, 0, frameSize);
        }
    }

    public void closeFrame() {
        frameSize = -1;
        wordPointer = -1;
        bitIndex = -1;
    }


    boolean isSyncCurrentPosition(int syncMode) throws IOException {
        int read = readBytes(syncBuffer, 0, 4);
        int headerString = ((syncBuffer[0] << 24) & 0xFF000000) | ((syncBuffer[1] << 16) & 0x00FF0000) | ((syncBuffer[2] << 8) & 0x0000FF00) | ((syncBuffer[3] << 0) & 0x000000FF);
        try {
            source.unread(syncBuffer, 0, read);
        } catch (IOException ex) {
        }
        if (read == 0) {
            return true;
        } else if (read == 4) {
            return isSyncMark(headerString, syncMode, syncWord);
        } else {
            return false;
        }
    }

    int syncHeader(byte syncMode) throws IOException {
        boolean sync;
        int headerString;
        int bytesRead = readBytes(syncBuffer, 0, 3);
        if (bytesRead != 3) {
            throw new EOFException();
        }
        headerString = ((syncBuffer[0] << 16) & 0x00FF0000) | ((syncBuffer[1] << 8) & 0x0000FF00) | ((syncBuffer[2] << 0) & 0x000000FF);
        do {
            headerString <<= 8;
            if (readBytes(syncBuffer, 3, 1) != 1) {
                throw new EOFException();
            }
            headerString |= (syncBuffer[3] & 0x000000FF);
            sync = isSyncMark(headerString, syncMode, syncWord);
        } while (!sync);
        return headerString;
    }

    private boolean isSyncMark(int headerString, int syncMode, int word) {
        boolean sync = false;
        if (syncMode == INITIAL_SYNC) {
            sync = ((headerString & 0xFFE00000) == 0xFFE00000); // SZD: MPEG 2.5
        } else {
            sync = ((headerString & 0xFFF80C00) == word) && (((headerString & 0x000000C0) == 0x000000C0) == singleChMode);
        }
        if (sync) {
            sync = (((headerString >>> 10) & 3) != 3);
        }
        if (sync) {
            sync = (((headerString >>> 17) & 3) != 0);
        }
        if (sync) {
            sync = (((headerString >>> 19) & 3) != 1);
        }
        return sync;
    }


    int readFrameData(int byteSize) throws IOException {
        int numread = 0;
        numread = readFully(frameBytes, 0, byteSize);
        frameSize = byteSize;
        wordPointer = -1;
        bitIndex = -1;
        return numread;
    }


    void parseFrame() {
        // Convert bytes read to int
        int b = 0;
        byte[] byteRead = frameBytes;
        int byteSize = frameSize;
        for (int k = 0; k < byteSize; k = k + 4) {
            byte b0 = 0;
            byte b1 = 0;
            byte b2 = 0;
            byte b3 = 0;
            b0 = byteRead[k];
            if (k + 1 < byteSize) {
                b1 = byteRead[k + 1];
            }
            if (k + 2 < byteSize) {
                b2 = byteRead[k + 2];
            }
            if (k + 3 < byteSize) {
                b3 = byteRead[k + 3];
            }
            frameBuffer[b++] = ((b0 << 24) & 0xFF000000) | ((b1 << 16) & 0x00FF0000) | ((b2 << 8) & 0x0000FF00) | (b3 & 0x000000FF);
        }
        wordPointer = 0;
        bitIndex = 0;
    }

    int getBits(int numberOfBits) {
        int returnValue = 0;
        int sum = bitIndex + numberOfBits;
        if (wordPointer < 0) {
            System.out.println("wordPointer < 0");
            wordPointer = 0;
        }
        if (sum <= 32) {
            returnValue = (frameBuffer[wordPointer] >>> (32 - sum)) & BITMASK[numberOfBits];
            bitIndex += numberOfBits;
            if (bitIndex == 32) {
                bitIndex = 0;
                wordPointer++;
            }
            return returnValue;
        }
        int right = (frameBuffer[wordPointer] & 0x0000FFFF);
        wordPointer++;
        int left = (frameBuffer[wordPointer] & 0xFFFF0000);
        returnValue = ((right << 16) & 0xFFFF0000) | ((left >>> 16) & 0x0000FFFF);
        returnValue >>>= 48 - sum;
        returnValue &= BITMASK[numberOfBits];
        bitIndex = sum - 32;
        return returnValue;
    }


    void setSyncWord(int s) {
        syncWord = s & 0xFFFFFF3F;
        singleChMode = ((s & 0x000000C0) == 0x000000C0);
    }


    private int readFully(byte[] b, int offs, int len) throws IOException {
        int read = 0;
        while (len > 0) {
            int bytesRead = source.read(b, offs, len);
            if (bytesRead == -1) {
                while (len-- > 0) {
                    b[offs++] = 0;
                }
                break;
            }
            read = read + bytesRead;
            offs += bytesRead;
            len -= bytesRead;
        }
        return read;
    }


    private int readBytes(byte[] b, int offs, int len) throws IOException {
        int totalBytesRead = 0;
        while (len > 0) {
            int bytesRead = source.read(b, offs, len);
            if (bytesRead == -1) {
                break;
            }
            totalBytesRead += bytesRead;
            offs += bytesRead;
            len -= bytesRead;
        }
        return totalBytesRead;
    }
}
