package com.zippy.ear;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: Store.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0012\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B'\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0003¢\u0006\u0004\b\t\u0010\nJ\t\u0010\u0012\u001a\u00020\u0003HÆ\u0003J\t\u0010\u0013\u001a\u00020\u0005HÆ\u0003J\t\u0010\u0014\u001a\u00020\u0007HÆ\u0003J\t\u0010\u0015\u001a\u00020\u0003HÆ\u0003J1\u0010\u0016\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u0003HÆ\u0001J\u0013\u0010\u0017\u001a\u00020\u00072\b\u0010\u0018\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\u0019\u001a\u00020\u001aHÖ\u0001J\t\u0010\u001b\u001a\u00020\u0003HÖ\u0001R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\u0006\u001a\u00020\u0007¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\b\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\f¨\u0006\u001c"}, d2 = {"Lcom/zippy/ear/Order;", "", "text", "", "at", "", "ok", "", "detail", "<init>", "(Ljava/lang/String;JZLjava/lang/String;)V", "getText", "()Ljava/lang/String;", "getAt", "()J", "getOk", "()Z", "getDetail", "component1", "component2", "component3", "component4", "copy", "equals", "other", "hashCode", "", "toString", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final /* data */ class Order {
    private final long at;
    private final String detail;
    private final boolean ok;
    private final String text;

    public static /* synthetic */ Order copy$default(Order order, String str, long j, boolean z, String str2, int i, Object obj) {
        if ((i & 1) != 0) {
            str = order.text;
        }
        if ((i & 2) != 0) {
            j = order.at;
        }
        long j2 = j;
        if ((i & 4) != 0) {
            z = order.ok;
        }
        boolean z2 = z;
        if ((i & 8) != 0) {
            str2 = order.detail;
        }
        return order.copy(str, j2, z2, str2);
    }

    /* JADX INFO: renamed from: component1, reason: from getter */
    public final String getText() {
        return this.text;
    }

    /* JADX INFO: renamed from: component2, reason: from getter */
    public final long getAt() {
        return this.at;
    }

    /* JADX INFO: renamed from: component3, reason: from getter */
    public final boolean getOk() {
        return this.ok;
    }

    /* JADX INFO: renamed from: component4, reason: from getter */
    public final String getDetail() {
        return this.detail;
    }

    public final Order copy(String text, long at, boolean ok, String detail) {
        Intrinsics.checkNotNullParameter(text, "text");
        Intrinsics.checkNotNullParameter(detail, "detail");
        return new Order(text, at, ok, detail);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Order)) {
            return false;
        }
        Order order = (Order) other;
        return Intrinsics.areEqual(this.text, order.text) && this.at == order.at && this.ok == order.ok && Intrinsics.areEqual(this.detail, order.detail);
    }

    public int hashCode() {
        return (((((this.text.hashCode() * 31) + Long.hashCode(this.at)) * 31) + Boolean.hashCode(this.ok)) * 31) + this.detail.hashCode();
    }

    public String toString() {
        return "Order(text=" + this.text + ", at=" + this.at + ", ok=" + this.ok + ", detail=" + this.detail + ")";
    }

    public Order(String text, long j, boolean z, String detail) {
        Intrinsics.checkNotNullParameter(text, "text");
        Intrinsics.checkNotNullParameter(detail, "detail");
        this.text = text;
        this.at = j;
        this.ok = z;
        this.detail = detail;
    }

    public final long getAt() {
        return this.at;
    }

    public final String getDetail() {
        return this.detail;
    }

    public final boolean getOk() {
        return this.ok;
    }

    public final String getText() {
        return this.text;
    }
}
