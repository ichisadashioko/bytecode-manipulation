package io.github.ichisadashioko;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.*;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

public final class BytecodeManipulation {
    public static void RenameClass(String oldName, String newName, Map<String, ClassNode> classMap) {

        Set<String> referenced = new HashSet<>();
        Map<String, ClassNode> updateMap = new HashMap<>();

        for (ClassNode cn : classMap.values()) {
            ClassNode updated = new ClassNode();
            cn.accept(new ClassRemapper(updated, new Remapper() {
                @Override
                public String map(String internalName) {
                    if (internalName.equals(oldName)) {
                        // mark classes that have reference to the `oldName` class
                        referenced.add(cn.name);

                        return newName;
                    }

                    return super.map(internalName);
                }
            }));

            if (referenced.contains(cn.name)) {
                updateMap.put(cn.name, updated);
            }
        }

        for (Entry<String, ClassNode> e : updateMap.entrySet()) {
            classMap.remove(e.getKey());
            ClassNode updated = e.getValue();
            if (updated.name.equals(newName) || updated.name.equals(oldName)) {
                // update the renamed class
                classMap.put(newName, updated);
            }else{
                // update the class that contains references to the renamed class
                classMap.put(updated.name, updated);
                classMap.remove(updated.name);
            }
        }
    }
}