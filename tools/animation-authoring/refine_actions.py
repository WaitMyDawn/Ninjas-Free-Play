"""Optional Blockbench document authoring and flat-floor contact bake; not part of the normal build."""
import argparse
import math
import json
import sys
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--mcp-root', type=Path, required=True)
parser.add_argument('--bake-only', action='store_true', help='Preserve limb keys and rebuild only supported roll root-height tracks.')
parser.add_argument('--actions', nargs='+', choices=['crouch_idle','crouch_walk','roll','roll_left','roll_right','roll_backward'],
                    help='Rebuild only the selected authored actions; other animation keyframes remain intact.')
parser.add_argument('--review-dir', type=Path, help='Write an offline geometry contact sheet, not a gameplay screenshot.')
parser.add_argument('--review-only', action='store_true', help='Render existing source clips without modifying the document.')
parser.add_argument('--review-actions', nargs='+', help='Names of existing clips to include in the geometry sheet.')
parser.add_argument('--review-phases', nargs='+', type=float, help='Normalized sampling phases between zero and one.')
parser.add_argument('--review-yaws', nargs='+', type=float, help='Camera yaw angles in degrees for each review row.')
args = parser.parse_args()
if args.review_only and (args.bake_only or args.actions): parser.error('--review-only cannot author or bake actions')
if args.review_phases and any(not math.isfinite(p) or not 0<=p<=1 for p in args.review_phases):
    parser.error('Review phases must be finite values between zero and one')
if args.review_yaws and any(not math.isfinite(yaw) for yaw in args.review_yaws):
    parser.error('Review camera yaws must be finite')
if args.bake_only and args.actions:
    parser.error('--actions cannot be combined with --bake-only')
selected = set() if args.review_only else set(args.actions or ['crouch_idle','crouch_walk','roll','roll_left','roll_right','roll_backward'])
sys.path.insert(0, str(args.mcp_root))
from blockbench_mcp.codecs import bbmodel

root = Path(__file__).resolve().parents[2]
source = root / 'animations/player_movement.bbmodel'
project = bbmodel.read(str(source))

def key(animation, bone, channel, time, values, velocity=None, step=0.055):
    slope = velocity or [0, 0, 0]
    project.add_keyframe(animation.uuid, bone, channel, time, values, interpolation='bezier', extra_fields={
        'bezier_left_time': [-step] * 3, 'bezier_right_time': [step] * 3,
        'bezier_left_value': [-v * step for v in slope], 'bezier_right_value': [v * step for v in slope], 'bezier_linked': True,
    })

def deformed_end(angle, center, length=12):
    angle = math.radians(angle)
    if abs(angle) < 1e-9:
        return [0, length, 0]
    width = 10
    start = center - width / 2
    distance = min(width, length - start)
    theta = angle * distance / width
    curvature = angle / width
    tail = max(0, length - start - width)
    return [0, start + math.sin(theta) / curvature + tail * math.cos(theta),
            (1 - math.cos(theta)) / curvature + tail * math.sin(theta)]

def crouch_arm_rotation(sign, phase):
    # Relaxed arms are oriented in entity model space before removing the torso's forward lean.
    spread = math.radians(-sign * (24 + sign * math.sin(phase) * 2))
    pitch = math.radians(15 + sign * math.sin(phase) * 3)
    body = math.radians(-52.5)
    columns = []
    for x, y, z in ((1,0,0), (0,1,0), (0,0,1)):
        y,z = y*math.cos(pitch)-z*math.sin(pitch), y*math.sin(pitch)+z*math.cos(pitch)
        x,y = x*math.cos(spread)-y*math.sin(spread), x*math.sin(spread)+y*math.cos(spread)
        y,z = y*math.cos(body)-z*math.sin(body), y*math.sin(body)+z*math.cos(body)
        columns.append((x,y,z))
    x = math.atan2(columns[1][2], columns[2][2])
    y = math.asin(max(-1, min(1, -columns[0][2])))
    z = math.atan2(columns[0][1], columns[0][0])
    return [-math.degrees(x), math.degrees(y), -math.degrees(z)]

def crouch(animation, time, phase=0, breathing=0):
    hips = []
    for side, sign in [('left', 1), ('right', -1)]:
        lead = (1 + sign * math.cos(phase)) / 2
        thigh = 35 + 55 * lead
        knee = 120 - 30 * lead
        theta = math.radians(thigh)
        drop = -math.inf
        for y in (index / 2 for index in range(25)):
            for z in (-2,2):
                angle=math.radians(knee)
                start=1
                distance=min(10,max(0,y-start))
                bend=angle*distance/10
                tail=max(0,y-start-10)
                if y<=start: dy,dz=y,z
                else:
                    dy=start+math.sin(bend)/(angle/10)+tail*math.cos(bend)-z*math.sin(bend)
                    dz=(1-math.cos(bend))/(angle/10)+tail*math.sin(bend)+z*math.cos(bend)
                drop=max(drop,math.cos(theta)*dy+math.sin(theta)*dz)
        lift = max(0, math.sin(phase) * sign) * 0.65
        hip = 24 - drop - lift
        hips.append(hip)
        key(animation, f'{side}_leg', 'position', time, [0, -(hip - 12), 0])
        key(animation, f'{side}_leg', 'rotation', time, [thigh, 0, -sign * 3])
        key(animation, f'{side}_shin', 'rotation', time, [-knee, 0, 0])
        key(animation, f'{side}_arm', 'position', time, [sign, 0, 0])
        key(animation, f'{side}_arm', 'rotation', time, crouch_arm_rotation(sign, phase))
        # The exporter negates forearm X; this sign places the palm ahead of the elbow in the entity frame.
        key(animation, f'{side}_forearm', 'rotation', time, [40 + sign * math.sin(phase) * 3, 0, 0])
    hip=sum(hips)/2+breathing
    key(animation, 'body', 'position', time, [0, -(hip-12), 0])
    key(animation, 'body', 'rotation', time, [-52.5, 0, 0])
    key(animation, 'head', 'position', time, [0, 0, 0])
    key(animation, 'head', 'rotation', time, [52.5, 0, 0])

if not args.bake_only:
    project.animations = [a for a in project.animations if a.name not in selected]
    if 'crouch_idle' in selected:
        idle = project.add_animation('crouch_idle', length=1, loop='loop')
        for t, breath in [(0, 0), (0.5, 0.08), (1, 0)]:
            crouch(idle, t, breathing=breath)
    if 'crouch_walk' in selected:
        walk = project.add_animation('crouch_walk', length=0.8, loop='loop')
        for index in range(9):
            crouch(walk, index / 10, phase=index * math.pi / 4)

    if 'roll' in selected:
        forward = project.add_animation('roll', length=0.6)
        # A long dive precedes shoulder/back contact; the limbs stay separated during the turn.
        for t,turn,lean,left,right,knees,arms,elbows in [
            (0,0,-10,15,25,(15,25),(115,100),(15,25)),
            (.07,-25,-15,20,30,(25,35),(130,115),(15,25)),
            (.14,-70,-10,45,60,(35,60),(120,90),(35,60)),
            (.25,-150,-70,40,55,(70,80),(90,70),(50,40)),
            (.36,-230,-40,35,45,(70,80),(70,50),(60,45)),
            (.48,-300,-20,55,75,(60,80),(60,45),(55,45)),
            (.6,-360,-25,25,40,(25,40),(25,10),(35,30))]:
            key(forward,'root','rotation',t,[turn,0,0])
            key(forward,'root','position',t,[0,0,0])
            key(forward,'body','position',t,[0,0,0])
            key(forward,'body','rotation',t,[lean,0,-5])
            key(forward,'head','position',t,[0,0,0])
            key(forward,'head','rotation',t,[-135 if t==.25 else -90 if t==.36 else -30 if t==.14 else -lean,0,5])
            for index,(side,sign) in enumerate([('left',1),('right',-1)]):
                key(forward,side+'_leg','position',t,[0,0,0])
                key(forward,side+'_leg','rotation',t,[left if index==0 else right,0,-sign*10])
                key(forward,side+'_shin','rotation',t,[-knees[index],0,0])
                key(forward,side+'_arm','position',t,[0,0,0])
                key(forward,side+'_arm','rotation',t,[arms[index],sign*8,sign*20])
                key(forward,side+'_forearm','rotation',t,[elbows[index],0,0])

    for name, axis, direction in [('roll_left', 2, -1), ('roll_right', 2, 1), ('roll_backward', 0, 1)]:
        if name not in selected:
            continue
        animation = project.add_animation(name, length=0.6)
        for phase, turn in [(0, 0), (0.12, 15), (0.25, 90), (0.5, 180), (0.72, 275), (0.88, 345), (1, 360)]:
            t = phase * 0.6
            rotation = [0, 0, 0]
            rotation[axis] = turn * direction
            key(animation, 'root', 'rotation', t, rotation)
            key(animation, 'root', 'position', t, [0, 0, 0])
            key(animation, 'body', 'position', t, [0, 0, 0])
            arch = (12 if phase == 0.12 else 8 if phase == 0.25 else -10 if phase in (0.72,0.88) else 0) if axis == 0 else 0
            key(animation, 'body', 'rotation', t, [arch, 0, 0])
            key(animation, 'head', 'position', t, [0, 0, 0])
            key(animation, 'head', 'rotation', t, [0, 0, 0])
            for side, sign in [('left', 1), ('right', -1)]:
                arm = [0, 0, 0]
                arm[axis] = -rotation[axis] - arch
                if axis == 0:
                    arm[2] = sign * (6 if 0.2 <= phase <= 0.6 else 18 + 12 * math.sin(math.pi * phase))
                shrug = 2 if 0.2 <= phase <= 0.6 else 1 if phase == 0.72 else 0
                key(animation, f'{side}_arm', 'position', t, [0, shrug, 0])
                knee = 0 if 0.2 <= phase <= 0.75 or phase in (0, 1) else -25
                key(animation, f'{side}_arm', 'rotation', t, arm)
                key(animation, f'{side}_forearm', 'rotation', t, [0, 0, 0])
                key(animation, f'{side}_leg', 'position', t, [0, 0, 0])
                spread = sign * 32 if 0.2 <= phase <= 0.75 and axis == 2 else 0
                if axis == 0:
                    spread = sign * (14 + 10 * math.sin(math.pi * phase))
                key(animation, f'{side}_leg', 'rotation', t, [sign * 8 * math.sin(math.pi * phase) if axis == 0 else 0, 0, spread])
                key(animation, f'{side}_shin', 'rotation', t, [knee, 0, 0])

def cubic(a, b, c, d, t):
    return (1-t)**3*a + 3*(1-t)**2*t*b + 3*(1-t)*t*t*c + t**3*d

def sample(animation, bone, channel, time):
    animator = animation.animators.get(project.group(bone).uuid)
    frames = [] if animator is None else animator.keyframes_of(channel)
    if not frames:
        return [0, 0, 0]
    if time <= frames[0].time:
        return list(frames[0].values)
    for first, last in zip(frames, frames[1:]):
        if time > last.time:
            continue
        if first.interpolation == 'step':
            return list(first.values)
        if first.interpolation != 'bezier' and last.interpolation != 'bezier':
            fraction = (time-first.time)/(last.time-first.time)
            return [a+(b-a)*fraction for a,b in zip(first.values,last.values)]
        result = []
        gap = last.time-first.time
        for axis in range(3):
            rt = min(gap, first.extra_fields.get('bezier_right_time', [0.1]*3)[axis])
            lt = max(-gap, last.extra_fields.get('bezier_left_time', [-0.1]*3)[axis])
            low, high = 0., 1.
            for _ in range(48):
                p = (low+high)/2
                if cubic(0,rt,gap+lt,gap,p) < time-first.time: low=p
                else: high=p
            rv = first.extra_fields.get('bezier_right_value', [0]*3)[axis]
            lv = last.extra_fields.get('bezier_left_value', [0]*3)[axis]
            result.append(cubic(first.values[axis], first.values[axis]+rv, last.values[axis]+lv, last.values[axis], (low+high)/2))
        return result
    return list(frames[-1].values)

def rotate(vector, angles):
    x,y,z=vector
    ax,ay,az=[math.radians(a) for a in angles]
    y,z=y*math.cos(ax)-z*math.sin(ax),y*math.sin(ax)+z*math.cos(ax)
    x,z=x*math.cos(ay)+z*math.sin(ay),-x*math.sin(ay)+z*math.cos(ay)
    return [x*math.cos(az)-y*math.sin(az),x*math.sin(az)+y*math.cos(az),z]

def model_rotation(values): return [-values[0], values[1], -values[2]]
def model_position(values): return [values[0], -values[1], values[2]]

def wrist(animation, side, time):
    body_rot=model_rotation(sample(animation,'body','rotation',time))
    body_pos=model_position(sample(animation,'body','position',time))
    arm_pos=model_position(sample(animation,side+'_arm','position',time))
    arm_rot=model_rotation(sample(animation,side+'_arm','rotation',time))
    bend=-sample(animation,side+'_forearm','rotation',time)[0]
    point=rotate(deformed_end(bend,4,length=10),arm_rot)
    anchor=[(5 if side=='left' else -5)+arm_pos[0],-10+arm_pos[1],arm_pos[2]]
    point=rotate([a+b for a,b in zip(point,anchor)],body_rot)
    return [point[0]+body_pos[0],point[1]+12+body_pos[1],point[2]+body_pos[2]]

for name in ['roll_left','roll_right','roll_backward']:
    if not args.bake_only and name not in selected:
        continue
    animation=project.animation(name)
    profile=json.loads((root/f'common/src/main/resources/assets/ninjas_free_play/ninja_contacts/{name}.json').read_text())['keys']
    samples=[]
    for index in range(37):
        time=animation.length*index/36
        phase=index/36
        weights=[0,0]
        for a,b in zip(profile,profile[1:]):
            if phase<=b['phase']:
                t=(phase-a['phase'])/(b['phase']-a['phase'])
                t=t*t*(3-2*t)
                weights=[a[field]+(b[field]-a[field])*t for field in ['left','right']]
                break
        old=sample(animation,'root','position',time)
        total=sum(weights)
        if total>0:
            rotation=model_rotation(sample(animation,'root','rotation',time))
            target=0
            for side,weight in zip(['left','right'],weights):
                point=wrist(animation,side,time)
                point[1]-=12
                target+=(12.016-rotate(point,rotation)[1])*weight/total
            old[1]=-target*max(weights)
        samples.append(old)
    animator=animation.animators[project.group('root').uuid]
    animator.keyframes=[frame for frame in animator.keyframes if frame.channel!='position']
    step=animation.length/36
    for index,point in enumerate(samples):
        if index in (0,len(samples)-1): velocity=[0,0,0]
        else: velocity=[(b-a)/(2*step) for a,b in zip(samples[index-1],samples[index+1])]
        key(animation,'root','position',index*step,point,velocity,step/3)

boxes = {
    'head': ([-4,-8,-4],[4,0,4]), 'body': ([-4,0,-2],[4,12,2]),
    'left_arm': ([-1,-2,-2],[3,10,2]), 'right_arm': ([-3,-2,-2],[1,10,2]),
    'left_leg': ([-2,0,-2],[2,12,2]), 'right_leg': ([-2,0,-2],[2,12,2]),
}

def deform_point(point, angle, center):
    angle=math.radians(angle)
    if abs(angle)<1e-9 or point[1]<=center-5: return point
    start=center-5
    distance=min(10,point[1]-start)
    theta=angle*distance/10
    tail=max(0,point[1]-start-10)
    y=start+math.sin(theta)/(angle/10)+tail*math.cos(theta)
    z=(1-math.cos(theta))/(angle/10)+tail*math.sin(theta)
    return [point[0],y-point[2]*math.sin(theta),z+point[2]*math.cos(theta)]

def geometry_point(animation,bone,point,time):
    pos=model_position(sample(animation,bone,'position',time))
    rotation=model_rotation(sample(animation,bone,'rotation',time))
    body_pos=model_position(sample(animation,'body','position',time))
    body_rot=model_rotation(sample(animation,'body','rotation',time))
    if bone in ('left_leg','right_leg'):
        bend=-sample(animation,'left_shin' if bone=='left_leg' else 'right_shin','rotation',time)[0]
        p=rotate(deform_point(point,bend,6),rotation)
        return [p[0]+pos[0]+(1.9 if bone=='left_leg' else -1.9),p[1]+pos[1]+12,p[2]+pos[2]]
    if bone in ('left_arm','right_arm'):
        bend=-sample(animation,'left_forearm' if bone=='left_arm' else 'right_forearm','rotation',time)[0]
        p=rotate(deform_point(point,bend,4),rotation)
        anchor=[5 if bone=='left_arm' else -5,2,0]
    elif bone=='head':
        p=rotate(point,rotation)
        anchor=[0,0,0]
    else:
        p=point
        pos=[0,0,0]
        anchor=[0,0,0]
    q=[p[0]+pos[0]+anchor[0],p[1]+pos[1]+anchor[1]-12,p[2]+pos[2]+anchor[2]]
    p=rotate(q,body_rot)
    return [p[0]+body_pos[0],p[1]+body_pos[1]+12,p[2]+body_pos[2]]

def vertices(animation,bone,time):
    low,high=boxes[bone]
    result=[]
    count=int((high[1]-low[1])*2)
    for i in range(count+1):
        y=low[1]+(high[1]-low[1])*i/count
        for x in (low[0],high[0]):
            for z in (low[2],high[2]): result.append(geometry_point(animation,bone,[x,y,z],time))
    return result

for forward in ([project.animation('roll')] if args.bake_only or 'roll' in selected else []):
    samples=[]
    for index in range(37):
        time=forward.length*index/36
        rotation=model_rotation(sample(forward,'root','rotation',time))
        maximum=-math.inf
        for bone in boxes:
            for point in vertices(forward,bone,time):
                point[1]-=12
                maximum=max(maximum,rotate(point,rotation)[1])
        phase=index/36
        rise=max(0,min(1,phase/.12));rise=rise*rise*(3-2*rise)
        land=max(0,min(1,(phase-.36)/.08));land=land*land*(3-2*land)
        clearance=.18*rise*(1-land)
        samples.append([0,-(12.016-maximum)+clearance*16/0.9375,0])
    animator=forward.animators[project.group('root').uuid]
    animator.keyframes=[frame for frame in animator.keyframes if frame.channel!='position']
    step=forward.length/36
    for index,point in enumerate(samples):
        velocity=[0,0,0] if index in (0,36) else [(b-a)/(2*step) for a,b in zip(samples[index-1],samples[index+1])]
        key(forward,'root','position',index*step,point,velocity,step/3)

if args.review_dir:
    from PIL import Image, ImageDraw
    args.review_dir.mkdir(parents=True,exist_ok=True)
    review_names=args.review_actions or ['crouch_idle','roll','roll_left','roll_right','roll_backward']
    review_phases=args.review_phases or [0,.18,.3,.5,.72]
    review_views=[(name,yaw) for name in review_names for yaw in (args.review_yaws or [48])]
    sheet=Image.new('RGB',(300*len(review_phases),250*len(review_views)),'#e5ebf0')
    colors={'head':(181,134,100),'body':(31,161,167),'left_arm':(194,145,112),'right_arm':(182,130,96),
            'left_leg':(46,74,151),'right_leg':(91,60,145)}
    for row,(name,view_yaw) in enumerate(review_views):
        animation=project.animation(name)
        for col,phase in enumerate(review_phases):
            time=animation.length*phase
            image=Image.new('RGB',(300,250),'#eef2f5')
            draw=ImageDraw.Draw(image)
            rotation=model_rotation(sample(animation,'root','rotation',time))
            pos=model_position(sample(animation,'root','position',time))
            def world(point):
                p=rotate([point[0],point[1]-12,point[2]],rotation)
                return [(p[0]+pos[0])/16, (12.016-p[1]-pos[1])/16, -(p[2]+pos[2])/16]
            def camera(p):
                x,y,z=p
                yaw=math.radians(view_yaw)
                a=x*math.cos(yaw)-z*math.sin(yaw)
                depth=x*math.sin(yaw)+z*math.cos(yaw)
                return (150+a*80,220-y*80+depth*20,depth)
            draw.line([(10,220),(290,220)],fill='#a0aab4',width=2)
            for grid in range(-3,4):
                a=camera([grid/2,0,-1.5]); b=camera([grid/2,0,1.5])
                draw.line([a[:2],b[:2]],fill='#d3dbe2')
                a=camera([-1.5,0,grid/2]); b=camera([1.5,0,grid/2])
                draw.line([a[:2],b[:2]],fill='#d3dbe2')
            faces=[]
            for bone,(low,high) in boxes.items():
                count=int(high[1]-low[1])
                for j in range(count):
                    y0=low[1]+j; y1=y0+1
                    pts=[[x,y,z] for x,y,z in [(low[0],y0,low[2]),(high[0],y0,low[2]),(high[0],y1,low[2]),(low[0],y1,low[2]),
                         (low[0],y0,high[2]),(high[0],y0,high[2]),(high[0],y1,high[2]),(low[0],y1,high[2])]]
                    pts=[camera(world(geometry_point(animation,bone,p,time))) for p in pts]
                    for face,light in [([0,1,2,3],.8),([4,7,6,5],1),([0,3,7,4],.7),([1,5,6,2],.9),([0,4,5,1],.65),([3,2,6,7],1.05)]:
                        color=tuple(min(255,int(c*light)) for c in colors[bone])
                        faces.append((sum(pts[i][2] for i in face)/4,[(pts[i][0],pts[i][1]) for i in face],color))
            for _,polygon,color in sorted(faces,key=lambda face:face[0]): draw.polygon(polygon,fill=color)
            draw.text((8,8),f'{name} phase={phase:.2f} view={view_yaw:g}',fill='#172d42')
            sheet.paste(image,(col*300,row*250))
    sheet.save(args.review_dir/'geometry-contact-sheet.png')

errors=project.validate()
if errors: raise ValueError(errors)
if not args.review_only:
    bbmodel.write(project,str(source))
    print(f'Saved {len(project.animations)} editable animations; selected actions: {sorted(selected)}.')
