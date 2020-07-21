namespace jvm com.getkeepsafe.dexcount.thrift;

struct MethodRef {
    1: string declaringClass;
    2: string returnType;
    3: string methodName;
    4: list<string> argumentTypes;
}

struct FieldRef {
    1: string declaringClass;
    2: string fieldType;
    3: string fieldName;
}

struct PackageTree {
    1: string name;
    2: bool isClass;
    3: map<string, PackageTree> children;
    4: set<MethodRef> declaredMethods;
    5: set<MethodRef> referencedMethods;
    6: set<FieldRef> declaredFields;
    7: set<FieldRef> referencedFields;
}

struct TreeGenOutput {
    1: PackageTree tree;
    2: string inputRepresentation;
}
