// package io.github.ichisadashioko;

// import java.io.IOException;
// import java.lang.instrument.Instrumentation;
// import java.util.*;
// import java.util.Map.Entry;
// import java.util.concurrent.ConcurrentHashMap;

// import org.objectweb.asm.commons.ClassRemapper;
// import org.objectweb.asm.commons.Remapper;
// import org.objectweb.asm.tree.*;

// import me.coley.recaf.workspace.ClassesMap;
// import me.coley.recaf.workspace.InputBuilder;

// public class RenameClass {
//     /**
//      * Map of class names to ClassNodes.
//      */
//     private final ClassesMap classMap = new ClassesMap();
//     /**
//      * Map of class names to ClassNode representations of the classes.
//      */
//     public final Set<String> classes = Collections.newSetFromMap(new ConcurrentHashMap<>());
//     /**
//      * The file loaded from.
//      */
//     public final File input;
//     /**
//      * Instrumentation instance loaded from.
//      */
//     private final Instrumentation instrumentation;

//     public RenameClass(Instrumentation instrumentation) throws IOException {
//         InputBuilder builder = new InputBuilder(instrumentation);
//         classMap.putAllRaw(builder.getClassContent());
//     }

//     public void renameClass(String original_name, String new_name) {
//         Set<String> referenced = new HashSet<>();
//         Map<String, ClassNode> updatedMap = new HashMap<>();
//         referenced.add(new_name);

//         // replace references in all classes
//         for (ClassNode cn : classMap.values()) {
//             ClassNode updated = new ClassNode();

//             cn.accept(new ClassRemapper(updated, new Remapper() {
//                 @Override
//                 public String map(String internalName) {
//                     if (internalName.equals(original_name)) {
//                         // mark classes that have referenced the renamed class.
//                         referenced.add(cn.name);
//                         return new_name;
//                     }
//                     return super.map(internalName);
//                 }
//             }));

//             if (referenced.contains(cn.name)) {
//                 updatedMap.put(cn.name, updated);
//             }
//         }

//         // update all classes with references to the renamed class
//         for (Entry<String, ClassNode> e : updatedMap.entrySet()) {
//             // remove old ClassNode
//             classMap.removeRaw(e.getKey());
//             // put and update new ClassNode
//             ClassNode updated = e.getValue();
//             if (updated.name.equals(new_name) || updated.name.equals(original_name)) {
//                 // udpate the renamed class (itself)
//                 classMap.put(updated.name, updated);
//             } else {
//                 // update the class that contains references to the renamed class
//                 classMap.put(updated.name, updated);
//                 classMap.remove(updated.name);
//             }
//         }

//         ClassNode node = classMap.get(new_name);
//         if (node == null) {
//             throw new RuntimeException(
//                     "Failed to fetch updated ClassNode for remapped class: " + original_name + " -> " + new_name);
//         }

//         // update inner classes
//         for (InnerClassNode innerNode : node.innerClasses) {
//             String inner = innerNode.name;
//             // ASM gives inner-clases a constant of themselves, copied from their parent
//             // so we skip self-referece values
//             if (inner.equals(original_name)) {
//                 continue;
//             }
//             // And skip self-referneces for renamed parents.
//             if (inner.equals(new_name)) {
//                 continue;
//             }

//             // Ensure the class exists. Can contain inner classes of other nodes
//             // that may not exist (such as Map$Entry)
//             if (!classes.contains(inner)) {
//                 continue;
//             }

//             // new name of inner, to be defined next
//             String newInner = null;

//             // check if inner/outer names exist. This will work for non-anonymous classes.
//             if (innerNode.outerName != null && innerNode.innerName != null) {
//                 // verfy that this should be renamed. don't want to mess with it if it is
//                 // defined by another class.
//                 if (!innerNode.outerName.equals(new_name)) {
//                     continue;
//                 }
//                 newInner = new_name + "$" + innerNode.innerName;
//             } else {
//                 // Deal with anonymous class
//                 // Inner ----------- Host ----------- Should rename? -- Case
//                 // Orig$1 ---------- Renmaed$Sub ---- NO -------------- 1
//                 // Orig$Sub$1 ------ Renmaed$Sub ---- YES ------------- 2
//                 // Renmaed$Sub ----- Renmaed$Sub$1 -- NO -------------- 3
//                 // Renmaed$1$1 ----- Renmaed$Sub$1 -- NO -------------- 4
//                 int splitIndex = inner.lastIndexOf("$");
//                 // verify the name of the inner class does not denote that it has been defined
//                 // in another class. This is case 4.
//                 String innerHost = inner.substring(0, splitIndex);
//                 if (!innerHost.equals(original_name) && !innerHost.equals(new_name)) {
//                     continue;
//                 }

//                 String innerName = inner.substring(splitIndex + 1);
//                 // account for case 3, where the inner is sorta an outer class.
//                 if (new_name.startsWith(inner) && inner.length() < new_name.length()) {
//                     continue;
//                 }
//                 newInner = new_name + "$" + innerName;
//             }
//             // send rename event if innerName is updated.
//             if (newInner != null && !inner.equals(newInner)){
                
//             }
//         }
//     }
// }