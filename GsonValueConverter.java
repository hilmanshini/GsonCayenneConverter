import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.access.ToManyList;
import org.apache.cayenne.access.ToOneFault;

import javax.swing.*;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.*;

public class GsonValueConverter {
    AbstractGsonValueConverter root;
    ConvertSettings mConvertSettings;
    private void construct(ConvertSettings mConvertSettings){
        PrimitiveConverter mPrimitiveConverter = new PrimitiveConverter(null, mConvertSettings);
        DateConverter mDateConverter = new DateConverter(mPrimitiveConverter,mConvertSettings);
        BeanConverter mBeanConverter = new BeanConverter(mDateConverter,mConvertSettings);
        CollectionConverter mCollectionConverter  =new CollectionConverter(mBeanConverter,mConvertSettings);
        root = mCollectionConverter;
        mPrimitiveConverter.root = root;
        mDateConverter.root = root;
        mBeanConverter.root = root;
        mCollectionConverter.root = root;
    }
    public JsonElement check(Object ez){
        return root.check(ez,null);
    }
    public GsonValueConverter(ConvertSettings mConvertSettings) {
        construct(mConvertSettings);
    }

    public GsonValueConverter() {
        construct(new ConvertSettings());

    }

    static abstract class AbstractGsonValueConverter {
        AbstractGsonValueConverter next;
        AbstractGsonValueConverter root;
        ConvertSettings mConvertSettings;

        public AbstractGsonValueConverter(AbstractGsonValueConverter next, ConvertSettings mConvertSettings) {
            this.next = next;
            this.mConvertSettings = mConvertSettings;
        }

        abstract JsonElement convert(JsonObject parent, String key, Object val, Object parentObject,AbstractGsonValueConverter root);

        abstract public boolean acceptClass(Class c);

        abstract JsonElement convertValue(Object o,AbstractGsonValueConverter root);


        public JsonElement check(Object val,Object parent){
            AbstractGsonValueConverter next = root;
            while (next != null){
                System.out.println("-> "+val.getClass());
                System.out.println("                                | "+next.getClass());
                if(next.acceptClass(val.getClass())){
                    System.out.println("========>              ACCEPTED");
                    System.out.println();
                    System.out.println();
                    return next.convertValue(val,root);
                }
                System.out.println("<-   next");
                next = next.next;
            }
            return null;
        }
    }

    static class DateConverter extends AbstractGsonValueConverter {

        public DateConverter(AbstractGsonValueConverter next, ConvertSettings mConvertSettings) {
            super(next, mConvertSettings);
        }

        @Override
        JsonElement convert(JsonObject parent, String key, Object val, Object parentObject,AbstractGsonValueConverter root) {
            parent.add(key, convertValue(val,root));
            return parent;
        }

        @Override
        public boolean acceptClass(Class c) {

            return LocalDateTime.class.isAssignableFrom(c);
        }

        @Override
        JsonElement convertValue(Object val,AbstractGsonValueConverter root) {
            LocalDateTime localDateTime = (LocalDateTime) val;
            Date d = new Date(localDateTime.get(ChronoField.MILLI_OF_SECOND));
            SimpleDateFormat sdf = new SimpleDateFormat(mConvertSettings.getDateFormat());
            return new JsonPrimitive(sdf.format(d));
        }
    }

    static class BeanConverter extends AbstractGsonValueConverter {

        private Map<String, Object> readValueFromObj(CayenneDataObject src) {
            Field[] f = CayenneDataObject.class.getDeclaredFields();
            try {
                for (Field field : f) {
                    field.setAccessible(true);
                    if (field.getName().equals("values")) {
                        Map<String, Object> es = (Map<String, Object>) field.get(src);
                        return es;
                    }
                }
            } catch (Exception e) {

            }

            return new HashMap<>();

        }

        public BeanConverter(AbstractGsonValueConverter next, ConvertSettings mConvertSettings) {
            super(next, mConvertSettings);
        }

        @Override
        JsonElement convert(JsonObject parent, String key, Object val, Object parentObject,AbstractGsonValueConverter root) {
            ;
            if(val instanceof CayenneDataObject){
                Object x = convertValue(val,root);
                if(x != null){
                    parent.add(key,convertValue(val,root));
                } else {

                }


            }
            return parent;
        }

        @Override
        public boolean acceptClass(Class c) {

            return CayenneDataObject.class.isAssignableFrom(c) ;
        }

        @Override
        JsonElement convertValue(Object val,AbstractGsonValueConverter root) {
            if(val instanceof ToOneFault){
                return new JsonObject();
            }
            CayenneDataObject object = (CayenneDataObject) val;
            Map<String,Object> props = readValueFromObj(object);
            Set<String> keys = props.keySet();
            JsonObject childJsonObject = new JsonObject();
            for (String s : keys) {
                Object valField = props.get(s);
                JsonElement childProp =  root.check(valField,val);
                if(childProp != null){
                    childJsonObject.add(s,childProp);
                }

            }
            return childJsonObject;
        }
    }

    static class CollectionConverter extends AbstractGsonValueConverter {

        public CollectionConverter(AbstractGsonValueConverter next, ConvertSettings mConvertSettings) {
            super(next, mConvertSettings);
        }

        @Override
        JsonElement convert(JsonObject parent, String key, Object val, Object parentObject,AbstractGsonValueConverter root) {
            System.out.println("converting " + val.getClass() + ' ' + (val instanceof ToManyList));
            if (val instanceof ToManyList || val instanceof Collection) {
                parent.add(key,convertValue(val,root));
            }
            return null;
        }

        @Override
        public boolean acceptClass(Class c) {

            Class val = c;
            return (Collection.class.isAssignableFrom(c) || ToManyList.class.isAssignableFrom(c));
        }

        @Override
        JsonElement convertValue(Object val, AbstractGsonValueConverter root) {
            if (val instanceof ToManyList) {
                System.out.println("Converting list");
                ToManyList mManySetFault = (ToManyList) val;
                JsonArray childArray = new JsonArray();
                for (Object o : mManySetFault) {
                    childArray.add(root.check(o,val));
                }
                return childArray;


            } else if(val instanceof Collection){

                JsonArray childArray = new JsonArray();
                Collection col = (Collection) val;

                Iterator i = col.iterator();
                while (i.hasNext()){
                    Object n = i.next();
                    System.out.println("CONVERT COL "+n.getClass());
                    childArray.add(root.check(n,val));
                }
                return childArray;
            }
            return new JsonArray();
        }
    }

    static class PrimitiveConverter extends AbstractGsonValueConverter {

        public PrimitiveConverter(AbstractGsonValueConverter next, ConvertSettings mConvertSettings) {
            super(next, mConvertSettings);
        }

        @Override
        JsonElement convert(JsonObject parent, String key, Object val, Object parentObject,AbstractGsonValueConverter root) {
            parent.add(key, new JsonPrimitive(val.toString()));
            return parent;
        }

        private static final Set<Class<?>> WRAPPER_TYPES = getWrapperTypes();

        public static boolean isWrapperType(Class<?> clazz) {
            return WRAPPER_TYPES.contains(clazz);
        }

        private static Set<Class<?>> getWrapperTypes() {
            Set<Class<?>> ret = new HashSet<Class<?>>();
            ret.add(Boolean.class);
            ret.add(Character.class);
            ret.add(Byte.class);
            ret.add(Short.class);
            ret.add(Integer.class);
            ret.add(String.class);
            ret.add(Long.class);
            ret.add(Float.class);
            ret.add(Double.class);
            ret.add(Void.class);
            return ret;
        }

        @Override
        public boolean acceptClass(Class c) {
            Class val = c;
            System.out.println("DEBUG                    ");

            return (val.isPrimitive() || isWrapperType(val) || String.class.isAssignableFrom(val));
        }

        @Override
        JsonElement convertValue(Object o, AbstractGsonValueConverter root) {
            return new JsonPrimitive(o.toString());
        }


    }


}

