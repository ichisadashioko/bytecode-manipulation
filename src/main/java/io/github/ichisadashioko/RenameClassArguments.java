package io.github.ichisadashioko;

import java.util.*;

import org.objectweb.asm.tree.ClassNode;

public class RenameClassArguments {
    public String oldClassName;
    public String newClassName;
    public Map<String, ClassNode> classNodes;
}