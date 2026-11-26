package com.zippy.ear;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.io.ByteStreamsKt;
import kotlin.io.CloseableKt;
import kotlin.io.FilesKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* JADX INFO: compiled from: ModelInstaller.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u000e\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007¨\u0006\b"}, d2 = {"Lcom/zippy/ear/ModelInstaller;", "", "<init>", "()V", "ensure", "Ljava/io/File;", "context", "Landroid/content/Context;", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class ModelInstaller {
    public static final ModelInstaller INSTANCE = new ModelInstaller();

    private ModelInstaller() {
    }

    public final File ensure(Context context) throws IOException {
        Intrinsics.checkNotNullParameter(context, "context");
        File file = new File(context.getFilesDir(), "vosk-model");
        File file2 = new File(file, "conf/model.conf");
        if (file2.exists()) {
            return file;
        }
        if (file.exists()) {
            FilesKt.deleteRecursively(file);
        }
        file.mkdirs();
        InputStream inputStreamOpen = context.getAssets().open("model.zip");
        try {
            ZipInputStream zipInputStream = new ZipInputStream(inputStreamOpen);
            try {
                ZipInputStream zipInputStream2 = zipInputStream;
                for (ZipEntry nextEntry = zipInputStream2.getNextEntry(); nextEntry != null; nextEntry = zipInputStream2.getNextEntry()) {
                    String name = nextEntry.getName();
                    Intrinsics.checkNotNullExpressionValue(name, "getName(...)");
                    String strSubstringAfter = StringsKt.substringAfter(name, '/', "");
                    if (strSubstringAfter.length() > 0 && !StringsKt.contains$default((CharSequence) strSubstringAfter, (CharSequence) "..", false, 2, (Object) null)) {
                        File file3 = new File(file, strSubstringAfter);
                        if (nextEntry.isDirectory()) {
                            file3.mkdirs();
                        } else {
                            File parentFile = file3.getParentFile();
                            if (parentFile != null) {
                                parentFile.mkdirs();
                            }
                            FileOutputStream fileOutputStream = new FileOutputStream(file3);
                            try {
                                Long.valueOf(ByteStreamsKt.copyTo$default(zipInputStream2, fileOutputStream, 0, 2, null));
                                CloseableKt.closeFinally(fileOutputStream, null);
                            } catch (Throwable th) {
                                try {
                                    throw th;
                                } catch (Throwable th2) {
                                    CloseableKt.closeFinally(fileOutputStream, th);
                                    throw th2;
                                }
                            }
                        }
                    }
                    zipInputStream2.closeEntry();
                }
                Unit unit = Unit.INSTANCE;
                CloseableKt.closeFinally(zipInputStream, null);
                Unit unit2 = Unit.INSTANCE;
                CloseableKt.closeFinally(inputStreamOpen, null);
                if (file2.exists()) {
                    return file;
                }
                throw new IllegalStateException("Speech model did not unpack.".toString());
            } catch (Throwable th3) {
                try {
                    throw th3;
                } catch (Throwable th4) {
                    CloseableKt.closeFinally(zipInputStream, th3);
                    throw th4;
                }
            }
        } catch (Throwable th5) {
            try {
                throw th5;
            } catch (Throwable th6) {
                CloseableKt.closeFinally(inputStreamOpen, th5);
                throw th6;
            }
        }
    }
}
