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
                String baseClassName = name.substring(0, name.lastIndexOf("."));
                classesData.put(baseClassName, value);

                ClassReader cr = new ClassReader(value);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.EXPAND_FRAMES);

                classNodes.put(baseClassName, cn);
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

    public static RenameClassReturns RenameClass(RenameClassArguments args) {
        String oldClassName = args.oldClassName;
        String newClassName = args.newClassName;
        Map<String, ClassNode> classNodes = args.classNodes;
        Set<String> modifiedClasses = new HashSet<>();

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
            ClassNode updated = e.getValue();

            if (updated.name.equals(oldClassName) || updated.name.equals(newClassName)) {
                classNodes.remove(oldClassName);
                classNodes.put(newClassName, updated);
            } else {
                classNodes.put(updated.name, updated);
                modifiedClasses.add(newClassName);
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
                RenameClassArguments innerArgs = new RenameClassArguments();
                innerArgs.oldClassName = inner;
                innerArgs.newClassName = newInnerClassName;
                innerRenameCalls.add(innerArgs);
            }
        }

        for (RenameClassArguments innerArgs : innerRenameCalls) {
            innerArgs.classNodes = classNodes;
            RenameClassReturns innerReturn = RenameClass(innerArgs);

            classNodes = innerReturn.classNodes;
            modifiedClasses.addAll(innerReturn.modifiedClasses);
        }

        RenameClassReturns retval = new RenameClassReturns();
        retval.classNodes = classNodes;
        retval.modifiedClasses = modifiedClasses;

        return retval;
    }

    public static void ExportJarFile(ExportJarArguments args) throws FileNotFoundException, IOException {
        Map<String, byte[]> contents = new TreeMap<>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                if (o1.contains("/") && !o2.contains("/")) {
                    return -1;
                } else if (!o1.contains("/") && o2.contains("/")) {
                    return 1;
                } else {
                    return o1.compareTo(o2);
                }
            }
        });

        for (Entry<String, ClassNode> e : args.classNodes.entrySet()) {
            String jarEntryKey = e.getKey() + ".class";

            if (args.modifiedClasses.contains(e.getKey()) || !args.originalClassData.containsKey(e.getKey())) {
                byte[] data = ConvertClassNodeToBytes(e.getValue());
                contents.put(jarEntryKey, data);
            } else {
                contents.put(jarEntryKey, args.originalClassData.get(e.getKey()));
            }
        }

        for (Entry<String, byte[]> e : args.resources.entrySet()) {
            contents.put(e.getKey(), e.getValue());
        }

        JarOutputStream output = new JarOutputStream(new FileOutputStream(args.outFile));
        Set<String> visitedDirs = new HashSet<>();

        for (Entry<String, byte[]> e : contents.entrySet()) {
            String key = e.getKey();
            if (key.contains("/")) {
                // record directories
                String parent = key;
                List<String> toAdd = new ArrayList<>();
                do {
                    parent = parent.substring(0, parent.lastIndexOf("/"));
                    if (!visitedDirs.contains(parent)) {
                        visitedDirs.add(parent);
                        toAdd.add(0, parent + "/");
                    }
                } while (parent.contains("/"));

                for (String dir : toAdd) {
                    output.putNextEntry(new JarEntry(dir));
                    output.closeEntry();
                }
            }

            output.putNextEntry(new JarEntry(key));
            output.write(e.getValue());
            output.closeEntry();
        }

        output.close();
    }

    public static byte[] ConvertClassNodeToBytes(ClassNode cn) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
