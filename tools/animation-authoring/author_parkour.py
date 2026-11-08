"""Author selected parkour baselines in the editable Blockbench source document."""
import argparse
import math
import sys
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--mcp-root',type=Path,required=True)
parser.add_argument('--actions',nargs='+',choices=['aim_glide','wall_latch','wall_latch_back','wall_latch_left','wall_latch_right','wall_dash','wall_dash_left','wall_dash_right','wall_dash_away','slide','air_flip','flying_kick'])
args=parser.parse_args()
sys.path.insert(0,str(args.mcp_root))
from blockbench_mcp.codecs import bbmodel

source=Path(__file__).resolve().parents[2]/'animations/player_movement.bbmodel'
project=bbmodel.read(str(source))
names=set(args.actions or ['aim_glide','wall_latch','wall_latch_back','wall_latch_left','wall_latch_right','wall_dash','wall_dash_left','wall_dash_right','wall_dash_away','slide','air_flip','flying_kick'])
project.animations=[a for a in project.animations if a.name not in names]

def create(name,**kwargs):
    return project.add_animation(name,**kwargs) if name in names else None

def key(animation,bone,channel,time,values):
    if animation is None: return
    step=min(0.04,animation.length/16)
    project.add_keyframe(animation.uuid,bone,channel,time,values,interpolation='bezier',extra_fields={
        'bezier_left_time':[-step]*3,'bezier_right_time':[step]*3,
        'bezier_left_value':[0]*3,'bezier_right_value':[0]*3,'bezier_linked':True})

def pose(animation,time,rotations,positions=None):
    for bone,rotation in rotations.items(): key(animation,bone,'rotation',time,rotation)
    for bone,position in (positions or {}).items(): key(animation,bone,'position',time,position)

glide=create('aim_glide',length=1.2,loop='loop')
for t,wave in [(0,0),(.6,1),(1.2,0)]:
    pose(glide,t,{'root':[-45,0,0], 'body':[0,0,0],'head':[35,0,0],
        'right_arm':[110,-8,-12],'right_forearm':[20,0,0],
        'left_arm':[-30,-15,58],'left_forearm':[20,0,0],
        'left_leg':[12,0,-12],'left_shin':[-30-wave*2,0,0],
        'right_leg':[-8,0,15],'right_shin':[-55+wave*2,0,0]}, {'root':[0,1+wave*.1,0]})

latch=create('wall_latch',length=1.2,loop='loop')
# Match the full unarmored foot support plane without translating the hip away from the torso.
def bent_reach(degrees):
    angle=math.radians(degrees)
    y=1+math.sin(angle)/(angle/10)+math.cos(angle)
    z=(1-math.cos(angle))/(angle/10)+math.sin(angle)
    return y*math.cos(angle)+z*math.sin(angle)
low,high=60.,120.
reach=12*math.sin(math.radians(30))+2*math.cos(math.radians(30))
for _ in range(60):
    middle=(low+high)/2
    if bent_reach(middle)>reach: low=middle
    else: high=middle
knee=(low+high)/2
def multiply(a,b):
    return [[sum(a[i][k]*b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

def rx(degrees):
    a=math.radians(degrees);c=math.cos(a);v=math.sin(a)
    return [[1,0,0],[0,c,-v],[0,v,c]]

def ry(degrees):
    a=math.radians(degrees);c=math.cos(a);v=math.sin(a)
    return [[c,0,v],[0,1,0],[-v,0,c]]

def authored(matrix):
    x=math.atan2(matrix[2][1],matrix[2][2])
    y=math.asin(max(-1,min(1,-matrix[2][0])))
    z=math.atan2(matrix[1][0],matrix[0][0])
    return [-math.degrees(x),math.degrees(y),-math.degrees(z)]

def transpose(matrix): return [list(row) for row in zip(*matrix)]

def transform(matrix,point): return [sum(a*b for a,b in zip(row,point)) for row in matrix]

def rz(degrees):
    a=math.radians(degrees);c=math.cos(a);v=math.sin(a)
    return [[c,-v,0],[v,c,0],[0,0,1]]

def runtime_rotation(degrees):
    return multiply(rz(-degrees[2]),multiply(ry(degrees[1]),rx(-degrees[0])))

def between(a,b):
    a=[x/math.sqrt(sum(v*v for v in a)) for x in a]
    b=[x/math.sqrt(sum(v*v for v in b)) for x in b]
    cross=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
    cosine=max(-1,min(1,sum(x*y for x,y in zip(a,b))))
    if cosine>1-1e-10: return [[1,0,0],[0,1,0],[0,0,1]]
    if cosine<-1+1e-10:
        axis=[0,-a[2],a[1]] if abs(a[0])<.8 else [-a[1],a[0],0]
        axis=[v/math.sqrt(sum(x*x for x in axis)) for v in axis]
        return [[2*axis[i]*axis[j]-(1 if i==j else 0) for j in range(3)] for i in range(3)]
    skew=[[0,-cross[2],cross[1]],[cross[2],0,-cross[0]],[-cross[1],cross[0],0]]
    squared=multiply(skew,skew)
    return [[(1 if i==j else 0)+skew[i][j]+squared[i][j]/(1+cosine) for j in range(3)] for i in range(3)]

def pulling_arm(rotation,body,root,elbow,side,pull):
    bend=math.radians(-elbow)
    curved=[0,10,0] if abs(bend)<1e-10 else [0,-1+math.sin(bend)/(bend/10)+math.cos(bend),(1-math.cos(bend))/(bend/10)+math.sin(bend)]
    parent=multiply(runtime_rotation(root),runtime_rotation(body))
    preferred=runtime_rotation(rotation)
    # Endpoints move along the wall in entity space, independent of torso twist and bank.
    desired=[side*(.85-1.7*pull),-.05,-.55]
    correction=between(transform(multiply(parent,preferred),curved),desired)
    return authored(multiply(transpose(parent),multiply(correction,multiply(parent,preferred))))

for name,turn,look in [('wall_latch',0,0),('wall_latch_back',180,180),
                       ('wall_latch_left',-65,-90),('wall_latch_right',65,90)]:
    animation=latch if name=='wall_latch' else create(name,length=1.2,loop='loop')
    body=multiply(rx(-30),ry(turn))
    inverse=[list(row) for row in zip(*body)]
    for t,wave in [(0,0),(.6,1),(1.2,0)]:
        rotations={'body':authored(body),'head':authored(multiply(inverse,ry(look))),
            'left_arm':[10+wave,0,20],'right_arm':[10-wave,0,-20],
            'left_forearm':[30,0,0],'right_forearm':[30,0,0],
            'left_leg':[30,0,-3],'left_shin':[0,0,0],
            'right_leg':[90+knee,0,50],'right_shin':[-knee,0,0]}
        if name=='wall_latch_back':
            rotations['right_leg']=[-10,0,-25]
            rotations['right_shin']=[100,0,0]
        elif turn!=0:
            rotations['left_leg'][1]=math.copysign(8,turn)
            rotations['right_leg']=[110,math.copysign(10,turn),math.copysign(25,turn)]
            rotations['right_shin']=[-20,0,0]
            rotations['left_arm'][2]+=8 if turn<0 else -4
            rotations['right_arm'][2]+=4 if turn<0 else -8
        pose(animation,t,rotations)

for name,side,away in [('wall_dash',0,False),('wall_dash_left',1,False),('wall_dash_right',-1,False),('wall_dash_away',0,True)]:
    dash=create(name,length=.35)
    # Reach ahead, pull the hands opposite the launch, then release and recover.
    for t,pull,recover in [(0,0,0),(.045,0,0),(.15,1,0),(.24,1,.5),(.35,1,1)]:
        support='right' if side<0 else 'left'
        trail='left' if support=='right' else 'right'
        rotations={'root':[-12*pull if away else 0,0,-side*12*pull*(1-recover)],
            'body':[15-25*pull+10*recover,-side*12*pull,0],'head':[-10+20*pull,0,0]}
        for arm,sign in [('left',1),('right',-1)]:
            if away:
                pitch=55+40*pull-70*recover;elbow=95-75*pull+15*recover;spread=sign*24
            elif side:
                pitch=80-35*pull-25*recover;elbow=35+55*pull-45*recover
                spread=-side*(65-115*pull)*(1-recover)+sign*18
            else:
                pitch=130-110*pull+sign*5*(1-recover);elbow=35+60*pull-50*recover;spread=sign*(18+6*pull)
            rotations[arm+'_arm']=[pitch,-side*10,spread]
            rotations[arm+'_forearm']=[elbow,0,0]
            if side:
                rotations[arm+'_arm']=pulling_arm(rotations[arm+'_arm'],rotations['body'],rotations['root'],elbow,side,pull*(1-recover))
        rotations[support+'_leg']=[145-115*pull+30*recover,0,-side*12]
        rotations[support+'_shin']=[-55*(1-pull)-25*recover,0,0]
        rotations[trail+'_leg']=[70+15*pull,0,side*14]
        rotations[trail+'_shin']=[-90+25*pull,0,0]
        pose(dash,t,rotations)

slide=create('slide',length=1,loop='loop')
for t,wave in [(0,0),(.5,1),(1,0)]:
    pose(slide,t,{'body':[30,0,-4],'head':[-30,0,4],
        'left_leg':[60,0,-4],'left_shin':[0,0,0],
        'right_leg':[105,0,-12],'right_shin':[-85,0,0],
        'left_arm':[55+wave*2,-8,28],'right_arm':[60-wave*2,8,-28],
        'left_forearm':[35,0,0],'right_forearm':[40,0,0]},
        {'body':[0,-4.2,0],'left_leg':[0,-4.2,0],'right_leg':[0,-4.2,0]})

flip=create('air_flip',length=.55)
for t,turn,tuck in [(0,0,0),(.1,-40,1),(.275,-180,1),(.45,-320,1),(.55,-360,0)]:
    pose(flip,t,{'root':[turn,0,0],'body':[-55*tuck,0,0],'head':[35*tuck,0,0],
        'left_arm':[50*tuck,0,15],'right_arm':[50*tuck,0,-15],
        'left_forearm':[65*tuck,0,0],'right_forearm':[65*tuck,0,0],
        'left_leg':[100*tuck,0,-4],'right_leg':[110*tuck,0,4],
        'left_shin':[-110*tuck,0,0],'right_shin':[-110*tuck,0,0]})

kick=create('flying_kick',length=1)
# The upper-leg pitch compensates the changing bend so the lower leg keeps its attack-facing axis.
for t,knee,extension in [(0,105,0),(.09,105,0),(.18,0,1),(.24,0,1),(1,0,1)]:
    root_rotation=[90,0,40+25*extension]
    body_rotation=[-8+3*extension,0,0]
    head_rotation=[-40,0,0]
    pose(kick,t,{'root':root_rotation,'body':body_rotation,'head':head_rotation,
        'left_leg':[knee,0,0],'left_shin':[-knee,0,0],
        'right_leg':[0,-5,-10],'right_shin':[-80,0,0],
        'left_arm':[-15,-12,35-7*extension],
        'right_arm':[-25,12,-30+5*extension],
        'left_forearm':[35,0,0],'right_forearm':[40,0,0]})

errors=project.validate()
if errors: raise ValueError(errors)
bbmodel.write(project,str(source))
print(f'Saved {len(project.animations)} editable animations; authored: {sorted(names)}')
