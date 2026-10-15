package local.quiettv.launcher;

/** Chooses a viewport that keeps the focused row and as many complete rows as possible. */
final class RowViewport {
    static int reveal(int[] tops,int[] bottoms,int focused,int height,int current,int maximum){
        if(focused<0||focused>=tops.length||tops.length!=bottoms.length||height<=0)return Math.max(0,current);
        maximum=Math.max(0,maximum);
        if(focused==0)return 0;
        int low=Math.max(0,bottoms[focused]-height),high=Math.min(maximum,tops[focused]);
        if(low>high)return Math.max(0,Math.min(maximum,tops[focused]));
        int best=clamp(current,low,high),count=count(tops,bottoms,best,height);
        for(int i=0;i<tops.length*2+2;i++){
            int candidate=i==tops.length*2?0:i==tops.length*2+1?maximum:i%2==0?tops[i/2]:bottoms[i/2]-height;
            candidate=clamp(candidate,low,high);int visible=count(tops,bottoms,candidate,height);
            if(visible>count||(visible==count&&Math.abs((long)candidate-current)<Math.abs((long)best-current))){best=candidate;count=visible;}
        }
        return best;
    }
    private static int clamp(int value,int low,int high){return Math.max(low,Math.min(high,value));}
    // Bounds are in document order; shared left/right bands may have equal bounds.
    private static int count(int[] tops,int[] bottoms,int y,int height){
        int first=lower(tops,y),end=lower(bottoms,(long)y+height+1);return Math.max(0,end-first);
    }
    private static int lower(int[] values,long value){int low=0,high=values.length;while(low<high){int mid=(low+high)>>>1;if(values[mid]<value)low=mid+1;else high=mid;}return low;}
    private RowViewport(){}
}
