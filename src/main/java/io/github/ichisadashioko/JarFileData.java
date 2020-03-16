package io.github.ichisadashioko;

import java.util.*;
import org.objectweb.asm.tree.*;

public class JarFileData {
    public Map<String, byte[]> classesData;
    public Map<String, byte[]> resources;
    public Map<String, ClassNode> classNodes;
}