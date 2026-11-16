import random
def table(base, slope, start=12, step=2, cap=50, curve=1.0):
    # chance per attempt at hour h (linear or curved), guarantee at cap
    out=[]
    h=start
    while h<cap:
        x=(h-start)/(48-start)
        p=base+(slope*(48-start))*(x**curve)
        out.append((h,min(p,1.0)))
        h+=step
    return out
def exact(tab,cap=50):
    s=1.0; mean=0.0; dist=[]
    for h,p in tab:
        mean+=s*p*h; dist.append((h,s*p)); s*=1-p
    mean+=s*cap; dist.append((cap,s))
    # median
    acc=0;med=None
    for h,q in dist:
        acc+=q
        if med is None and acc>=0.5: med=h
    p90=None;acc=0
    for h,q in dist:
        acc+=q
        if p90 is None and acc>=0.9: p90=h
    return mean,med,p90,s
def mc(tab,cap=50,n=1_000_000,seed=151):
    r=random.Random(seed); tot=0; pity=0
    for _ in range(n):
        t=cap
        for h,p in tab:
            if r.random()<p: t=h;break
        else: pity+=1
        tot+=t
    return tot/n,pity/n
for name,tab in [("demande (1,2 %% a 12 h, +0,25 %%/h, 10,2 %% a 48 h)",table(0.012,0.0025)),
                 ("variante B (1,2 %% a 12 h, courbe x^1.6 jusqu'a 22 %% a 48 h)",table(0.012,(0.22-0.012)/36,curve=1.6)),
                 ("variante C (2 %% a 12 h, +0,5 %%/h, 20 %% a 48 h)",table(0.02,0.005))]:
    m,med,p90,pity=exact(tab)
    mm,mp=mc(tab,n=400000)
    print(name.replace('%%','%'))
    print("  exact: moyenne %.2f h, mediane %d h, 90e centile %d h, garantie 50 h atteinte %.1f %%" % (m,med,p90,pity*100))
    print("  Monte-Carlo 400k: moyenne %.2f h, garantie %.1f %%" % (mm,mp*100))
    print("  " + ", ".join("%dh:%.1f%%"%(h,p*100) for h,p in tab if h in (12,20,30,40,48)))
