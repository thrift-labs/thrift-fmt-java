package thriftlabs.thriftfmt;

public class Option {
    public static final int DEFAULT_INDENT = 4;
    // We use a comma as seprator, and this is not exported to set(maybe some day).
    public static final String DEFAULT_SEPARATOR = ",";

    private int indent;
    private boolean patchRequired;
    private boolean patchSeparator;
    private boolean keepComment;
    private boolean alignByAssign;
    private boolean alignByField;

    public Option() {
        this(DEFAULT_INDENT, true, true, true, true, false);
    }

    public Option(int indent, boolean patchRequired, boolean patchSeparator, boolean keepComment, boolean alignByAssign,
            boolean alignByField) {
        if (indent > 0) {
            this.indent = indent;
        } else {
            this.indent = DEFAULT_INDENT;
        }

        this.patchRequired = patchRequired;
        this.patchSeparator = patchSeparator;
        this.keepComment = keepComment;

        this.alignByAssign = alignByAssign;
        this.alignByField = !alignByAssign && alignByField;
    }

    public int getIndent() {
        return indent;
    }

    public boolean isPatchRequired() {
        return patchRequired;
    }

    public boolean isPatchSeparator() {
        return patchSeparator;
    }

    public boolean isKeepComment() {
        return keepComment;
    }

    public boolean isAlignByAssign() {
        return alignByAssign;
    }

    public boolean isAlignByField() {
        return alignByField;
    }
}
