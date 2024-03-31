/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc;

import icyllis.modernui.core.UndoOperation;
import icyllis.modernui.core.UndoOwner;
import icyllis.modernui.util.Parcel;
import net.minecraft.client.gui.components.EditBox;

import javax.annotation.Nonnull;

public class EditBoxEditAction extends UndoOperation<EditBox> {

    private final boolean mIsInsert;

    private final String mOldText;
    private String mNewText;
    private final int mStart;

    private final int mOldCursorPos;
    private int mNewCursorPos;

    public EditBoxEditAction(UndoOwner owner, int cursor, String oldText,
                             int start, String newText) {
        super(owner);
        mOldText = oldText;
        mNewText = newText;
        mIsInsert = !mNewText.isEmpty() && mOldText.isEmpty();
        mStart = start;
        mOldCursorPos = cursor;
        mNewCursorPos = start + mNewText.length();
    }

    @Override
    public void commit() {
    }

    @Override
    public void undo() {
        EditBox target = getOwnerData();
        applyUndoOrRedo(
                target,
                mStart,
                mStart + mNewText.length(),
                mOldText,
                mStart,
                mOldCursorPos
        );
    }

    @Override
    public void redo() {
        EditBox target = getOwnerData();
        applyUndoOrRedo(
                target,
                mStart,
                mStart + mOldText.length(),
                mNewText,
                mStart,
                mNewCursorPos
        );
    }

    private static void applyUndoOrRedo(EditBox target,
                                        int deleteFrom, int deleteTo,
                                        CharSequence newText, int newTextInsertAt,
                                        int newCursorPos) {
        StringBuilder text = new StringBuilder(target.getValue());
        if (0 <= deleteFrom && deleteFrom <= deleteTo && deleteTo <= text.length()
                && newTextInsertAt <= text.length() - (deleteTo - deleteFrom)) {
            if (deleteFrom != deleteTo) {
                text.delete(deleteFrom, deleteTo);
            }
            if (!newText.isEmpty()) {
                text.insert(newTextInsertAt, newText);
            }
            target.setValue(text.toString());
        }
        if (0 <= newCursorPos && newCursorPos <= text.length()) {
            target.setCursorPosition(newCursorPos);
            target.setHighlightPos(newCursorPos);
        }
    }

    @Override
    public void writeToParcel(@Nonnull Parcel dest, int flags) {
        // NO DATA
    }

    public boolean mergeInsertWith(EditBoxEditAction edit) {
        if (mIsInsert && edit.mIsInsert) {
            if (mStart + mNewText.length() != edit.mStart) {
                return false;
            }
            mNewText += edit.mNewText;
            mNewCursorPos = edit.mNewCursorPos;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "EditBoxEditAction{" +
                "mOldText=" + mOldText +
                ", mNewText=" + mNewText +
                ", mStart=" + mStart +
                ", mOldCursorPos=" + mOldCursorPos +
                ", mNewCursorPos=" + mNewCursorPos +
                '}';
    }
}
