namespace jvm com.getkeepsafe.dexcount.thrift;

struct MethodRef {
    1: i64 declaringClass;
    2: i64 returnType;
    3: i64 methodName;
    4: list<i64> argumentTypes;
}

struct FieldRef {
    1: i64 declaringClass;
    2: i64 fieldType;
    3: i64 fieldName;
}

struct PackageTree {
    1: i64 name;
    2: bool isClass;
    3: map<i64, PackageTree> children;
    4: set<MethodRef> declaredMethods;
    5: set<MethodRef> referencedMethods;
    6: set<FieldRef> declaredFields;
    7: set<FieldRef> referencedFields;
}

struct TreeGenOutput {
    1: PackageTree tree;
    2: string inputRepresentation;
    3: map<i64, string> stringPool;
}
