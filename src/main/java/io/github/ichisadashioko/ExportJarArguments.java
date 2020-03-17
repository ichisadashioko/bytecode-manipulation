package io.github.ichisadashioko;

import java.io.File;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;

public class ExportJarArguments {
    File outFile;
    Map<String, byte[]> originalClassData;
    Map<String, byte[]> resources;
    Map<String, ClassNode> classNodes;
    Set<String> modifiedClasses;
}