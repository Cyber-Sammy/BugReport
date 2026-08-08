package com.cybersammy.bugreport.core.packaging;

/** Product-owned progress observer for a blocking report ZIP write. */
@FunctionalInterface
public interface ReportZipProgressListener {
    void onProgress(ReportZipProgress progress);

    static ReportZipProgressListener noOp() {
        return progress -> {};
    }
}
