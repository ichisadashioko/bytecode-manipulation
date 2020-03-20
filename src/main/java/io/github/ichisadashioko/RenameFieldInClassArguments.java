package io.github.ichisadashioko;

import java.util.Map;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class RenameFieldInClassArguments {
    public ClassNode fieldOwner;
    FieldNode field;
    public String oldFieldName;
    public String newFieldName;
    public Map<String, ClassNode> classNodes;
}