package io.github.ichisadashioko;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.*;
import java.util.zip.*;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

public final class BytecodeManipulation {
    public static JarFileData ParseJarFile(File input) throws IOException {

        Map<String, byte[]> classesData = new HashMap<>();
        Map<String, ClassNode> classNodes = new HashMap<>();

        Map<String, byte[]> resources = new HashMap<>();

        ZipFile zipFile = new ZipFile(input);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }

            InputStream is = zipFile.getInputStream(entry);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;

            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }

            byte[] value = os.toByteArray();
            String name = entry.getName();
            // System.out.println(name);

            if (name.endsWith(".class")) {
                classesData.put(name, value);

                ClassReader cr = new ClassReader(value);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.EXPAND_FRAMES);
                classNodes.put(name, cn);
            } else {
                resources.put(name, value);
            }
        }

        zipFile.close();

        JarFileData data = new JarFileData();
        data.classesData = classesData;
        data.resources = resources;
        data.classNodes = classNodes;

        return data;
    }

    public static Map<String, ClassNode> RenameClass(RenameClassArguments args) {
        String oldClassName = args.oldClassName;
        String newClassName = args.newClassName;
        Map<String, ClassNode> classNodes = args.classNodes;

        Set<String> referenced = new HashSet<>();
        Map<String, ClassNode> updateMap = new HashMap<>();

        for (ClassNode cn : classNodes.values()) {
            // System.out.println(String.format("cn.name: %s", cn.name));
            ClassNode updated = new ClassNode();
            cn.accept(new ClassRemapper(updated, new Remapper() {
                @Override
                public String map(String internalName) {
                    if (internalName.equals(oldClassName)) {
                        referenced.add(cn.name);
                        // System.out.println(String.format("internalName: %s, cn.name: %s",
                        // internalName, cn.name));
                        return newClassName;
                    }
                    return super.map(internalName);
                }
            }));

            if (referenced.contains(cn.name)) {
                updateMap.put(cn.name, updated);
            }
        }

        for (Entry<String, ClassNode> e : updateMap.entrySet()) {
            // String name = e.getKey();
            ClassNode updated = e.getValue();

            if (updated.name.equals(oldClassName)) {
                classNodes.put(newClassName, updated);
            } else {
                classNodes.put(updated.name, updated);
            }
        }

        ClassNode node = classNodes.get(newClassName);
        if (node == null) {
            throw new RuntimeException();
        }

        // update inner classes
        List<RenameClassArguments> innerRenameCalls = new ArrayList<>();

        for (InnerClassNode innerNode : node.innerClasses) {
            String inner = innerNode.name;

            if (inner.equals(oldClassName)) {
                continue;
            }

            if (inner.equals(newClassName)) {
                continue;
            }

            // Ensure the class exist
            if (!classNodes.containsKey(inner)) {
                continue;
            }

            String newInnerClassName = null;
            if (innerNode.outerName != null && innerNode.innerName != null) {
                if (!innerNode.outerName.equals(newClassName)) {
                    continue;
                }

                newInnerClassName = newClassName + "$" + innerNode.innerName;
            } else {
                int splitIndex = inner.lastIndexOf("$");
                String innerHost = inner.substring(0, splitIndex);
                if (!innerHost.equals(oldClassName) && !innerHost.equals(newClassName)) {
                    continue;
                }

                String innerName = inner.substring(splitIndex + 1);

                if (newClassName.startsWith(inner) && inner.length() < newClassName.length()) {
                    continue;
                }

                newInnerClassName = newClassName + "$" + innerName;
            }

            if (newInnerClassName != null && !inner.equals(newInnerClassName)) {
                System.out.println(String.format("newInnerClassName: %s, inner: %s", newInnerClassName, inner));
                // TODO call rename(classNodes.get(inner), inner, innerNewClassName)
                RenameClassArguments innerArgs = new RenameClassArguments();
                innerArgs.oldClassName = inner;
                innerArgs.newClassName = newInnerClassName;
                innerRenameCalls.add(innerArgs);
            }
        }

        for (int i = 0; i < innerRenameCalls.size(); i++) {
            RenameClassArguments innerArgs = innerRenameCalls.get(i);
            innerArgs.classNodes = classNodes;
            classNodes = RenameClass(innerArgs);
        }

        return classNodes;
    }
}
