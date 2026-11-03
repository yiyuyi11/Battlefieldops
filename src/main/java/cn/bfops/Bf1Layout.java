package cn.bfops;

/** Shared render/hit-test geometry for the full-screen deployment view. */
final class Bf1Layout {
    final Layout.Rect loadout, deploy, squad, options,weapon;
    final int columns;
    Bf1Layout(int width, int height) {
        weapon=width>=620&&height>=300?new Layout.Rect(width/2-100,height-58-62,200,56):new Layout.Rect(0,0,0,0);
        boolean wide = width >= 620;
        columns = wide ? 8 : 4;
        int stripHeight = height>=260?(wide?48:72):(wide?28:50);
        int stripY = height - (wide ? stripHeight+10 : stripHeight+36);
        loadout = new Layout.Rect(wide ? 132 : 8, stripY,
                wide ? width - 284 : width - 16, stripHeight);
        deploy = new Layout.Rect(width - 144, height - 32, 136, 24);
        int squadY = Math.max(68, stripY - 88);
        squad = new Layout.Rect(8, squadY, 116, 20);
        int rightW = Math.min(136, Math.max(100, width / 4));
        int bottom = stripY - 6;
        options = new Layout.Rect(width - rightW - 8, Math.max(68, bottom - 104),
                rightW, Math.max(18, bottom - Math.max(68, bottom - 104)));
    }
    Layout.Rect slot(int index) {
        int rows = 8 / columns;
        Layout.Rect row = Layout.rows(loadout, rows, 2)[index / columns];
        return Layout.columns(row, columns, 2)[index % columns];
    }
}
