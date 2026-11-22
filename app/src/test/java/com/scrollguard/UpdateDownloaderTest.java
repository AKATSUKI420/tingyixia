package com.scrollguard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UpdateDownloaderTest {

    @Test
    public void hashesThePublishedBytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223"
                        + "b00361a396177a9cb410ff61f20015ad",
                UpdateDownloader.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
