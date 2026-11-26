package com.zippy.ear;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: Webhook.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005¢\u0006\u0004\b\u0006\u0010\u0007J\t\u0010\f\u001a\u00020\u0003HÆ\u0003J\t\u0010\r\u001a\u00020\u0005HÆ\u0003J\u001d\u0010\u000e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005HÆ\u0001J\u0013\u0010\u000f\u001a\u00020\u00032\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\u0011\u001a\u00020\u0012HÖ\u0001J\t\u0010\u0013\u001a\u00020\u0005HÖ\u0001R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000b¨\u0006\u0014"}, d2 = {"Lcom/zippy/ear/SendResult;", "", "ok", "", "detail", "", "<init>", "(ZLjava/lang/String;)V", "getOk", "()Z", "getDetail", "()Ljava/lang/String;", "component1", "component2", "copy", "equals", "other", "hashCode", "", "toString", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final /* data */ class SendResult {
    private final String detail;
    private final boolean ok;

    public static /* synthetic */ SendResult copy$default(SendResult sendResult, boolean z, String str, int i, Object obj) {
        if ((i & 1) != 0) {
            z = sendResult.ok;
        }
        if ((i & 2) != 0) {
            str = sendResult.detail;
        }
        return sendResult.copy(z, str);
    }

    /* JADX INFO: renamed from: component1, reason: from getter */
    public final boolean getOk() {
        return this.ok;
    }

    /* JADX INFO: renamed from: component2, reason: from getter */
    public final String getDetail() {
        return this.detail;
    }

    public final SendResult copy(boolean ok, String detail) {
        Intrinsics.checkNotNullParameter(detail, "detail");
        return new SendResult(ok, detail);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SendResult)) {
            return false;
        }
        SendResult sendResult = (SendResult) other;
        return this.ok == sendResult.ok && Intrinsics.areEqual(this.detail, sendResult.detail);
    }

    public int hashCode() {
        return (Boolean.hashCode(this.ok) * 31) + this.detail.hashCode();
    }

    public String toString() {
        return "SendResult(ok=" + this.ok + ", detail=" + this.detail + ")";
    }

    public SendResult(boolean z, String detail) {
        Intrinsics.checkNotNullParameter(detail, "detail");
        this.ok = z;
        this.detail = detail;
    }

    public final String getDetail() {
        return this.detail;
    }

    public final boolean getOk() {
        return this.ok;
    }
}
