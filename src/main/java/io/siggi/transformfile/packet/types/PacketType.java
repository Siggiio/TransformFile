package io.siggi.transformfile.packet.types;

public enum PacketType {
    FILE_LIST,
    DATA_CHUNK,
    FILE_NAME,
    PARENT_DIRECTORY_DISTANCE,
    CLOSE_FILE,
    OFFSETS,
    END;
}
