//******************************************************************************************
//* Scherk-Collins Surface, by Paul Nylander, bugman123.com, 7/19/06
//* runtime: ? minutes
//******************************************************************************************

camera{
	//location <0,7*pi,-14*pi/3>/1.25 
	location <sin(2*pi*clock)*25, sin(4*pi*clock)*5+10, cos(2*pi*clock)*25>+<0,0,-pi/3> 
	look_at <0,0,-pi/3> 
	right x*image_width/image_height 
	up z 
	sky z
}

#declare theta=0;
#while(theta<2*pi)
 light_source{<10*cos(theta),10*sin(theta),-5>,0.8} // 1
 #declare theta=theta+2*pi/5;
#end

//Basic Functions
#macro square(X) X*X #end
#macro cot(theta) tan(pi/2-theta) #end
#macro ln2(X) #if(X>0) #local Y=ln(X); #else #local Y=0; #end Y #end
#macro atan3(Y,X) #if(X=0 & Y=0) #local theta=0; #else #local theta=atan2(Y,X); #end theta #end
#macro hue(h) #local X=mod(6*h,1); #local c=<0,0,0>;
 #switch(mod(floor(6*h),6))
  #case(0) #local c=<1,X,0>; #break
  #case(1) #local c=<1-X,1,0>; #break
  #case(2) #local c=<0,1,X>; #break
  #case(3) #local c=<0,1-X,1>; #break
  #case(4) #local c=<X,0,1>; #break
  #case(5) #local c=<1,0,1-X>; #break
 #end
 c
#end

//Complex Functions
#macro Complex(X,Y) <X,Y> #end
#declare I=Complex(0,1);
#macro Re(Z) Z.x #end
#macro Im(Z) Z.y #end
#macro Abs(Z) vlength(Z) #end
#macro Arg(Z) atan3(Im(Z),Re(Z)) #end
#macro Sqr(Z) Complex(square(Re(Z))-square(Im(Z)),2*Re(Z)*Im(Z)) #end
#macro Pow(Z,n) #local r=Abs(Z); #if(r=0) #local z2=Complex(0,0); #else #local theta=n*Arg(Z); #local z2=pow(r,n)*Complex(cos(theta),sin(theta)); #end z2 #end
#macro Sqrt(Z) Pow(Z,1/2) #end
#macro Mult(z1,z2) Complex(Re(z1)*Re(z2)-Im(z1)*Im(z2),Im(z1)*Re(z2)+Re(z1)*Im(z2)) #end
#macro Div(z1,z2) Mult(z1,Pow(z2,-1)) #end
#macro Exp(Z) exp(Re(Z))*Complex(cos(Im(Z)),sin(Im(Z))) #end
#macro Ln(Z) Complex(ln2(Abs(Z)),Arg(Z)) #end
#macro ArcTan(Z) #local z1=Complex(1,0)+Mult(I,Z); #local z2=Complex(1,0)-Mult(I,Z); Complex(Arg(z1)-Arg(z2),ln(Abs(z2))-ln(Abs(z1)))/2 #end

//Parametric Plotting, function f must be defined before this function can be called
#macro ParametricPlot3D(u1,u2,du, v1,v2,dv) #local imax=int((u2-u1)/du); #local jmax=int((v2-v1)/dv);
 #local Mesh=array[imax+1][jmax+1]; #local i=0;
 #while(i<=imax) #local U=u1+i*du; #local j=0; #while(j<=jmax) #local Mesh[i][j]=f(U,v1+j*dv); #local j=j+1; #end #local i=i+1; #end
 DrawMesh(Mesh)
#end
#macro pt(p) <p.x,p.y,p.z> #end
#macro det3(a,b,c,d,e,f,g,h,i) -c*e*g+b*f*g+c*d*h-a*f*h-b*d*i+a*e*i #end // 3×3 matrix
#macro color_triangle(p1,n1,c1, p2,n2,c2, p3,n3,c3) // adapted from Chris Colefax's triangle mapping macro
 #local nx=p2-p1; #local ny=p3-p1; #local nz=vcross(nx,ny);
 smooth_triangle{p1,n1,p2,n2,p3,n3 texture{
  #if(det3(nx.x,nx.y,nx.z, ny.x,ny.y,ny.z, nz.x,nz.y,nz.z)=0) pigment{rgb c1} #else pigment{
   average pigment_map{
    [1 gradient x color_map{[0 rgbt <0,0,0,0.5>][1 rgbt <3*c2.x,3*c2.y,3*c2.z,0.5>]}]
    [1 gradient y color_map{[0 rgbt <0,0,0,0.5>][1 rgbt <3*c3.x,3*c3.y,3*c3.z,0.5>]}]
    [1 gradient z color_map{[0 rgbt <0,0,0,0.5>][1 rgbt <3*c1.x,3*c1.y,3*c1.z,0.5>]}]
   }
   matrix <1.01,0,1, 0,1.01,1, 0,0,1, -0.002,-0.002,-1>
   matrix <nx.x,nx.y,nx.z, ny.x,ny.y,ny.z, nz.x,nz.y,nz.z, p1.x,p1.y,p1.z>
  } #end
  finish{reflection 0.4}
 }}
#end
#macro colorquad(p1,n1,c1, p2,n2,c2, p3,n3,c3, p4,n4,c4)
 #if(vlength(p3-p1)<vlength(p4-p2))
  color_triangle(p1,n1,c1, p2,n2,c2, p3,n3,c3) color_triangle(p1,n1,c1, p3,n3,c3, p4,n4,c4)
 #else
  color_triangle(p1,n1,c1, p2,n2,c2, p4,n4,c4) color_triangle(p2,n2,c2, p3,n3,c3, p4,n4,c4)
 #end
#end
#macro DrawMesh(Mesh)
 #local imax=dimension_size(Mesh,1); #local jmax=dimension_size(Mesh,2); #local normals=array[imax][jmax];
 #local i=0;
 #while(i<imax) #local j=0; #while(j<jmax) #local normals[i][j]=<0,0,0>; #local j=j+1; #end #local i=i+1; #end
 #local i=0;
 #while(i<imax-1) #local j=0; // adapted from Tim Wenclawiak's Make_Normals function
  #while(j<jmax-1)
   #local p0=(Mesh[i][j]+Mesh[i][j+1]+Mesh[i+1][j]+Mesh[i+1][j+1])/4;
   #local N=vcross(pt(Mesh[i+1][j]-p0),pt(Mesh[i+1][j+1]-p0));
   #local E=vcross(pt(Mesh[i+1][j+1]-p0),pt(Mesh[i][j+1]-p0));
   #local S=vcross(pt(Mesh[i][j+1]-p0),pt(Mesh[i][j]-p0));
   #local W=vcross(pt(Mesh[i][j]-p0),pt(Mesh[i+1][j]-p0));
   #local normals[i][j]=normals[i][j]+S+W;
   #local normals[i+1][j]=normals[i+1][j]+N+W;
   #local normals[i][j+1]=normals[i][j+1]+S+E;
   #local normals[i+1][j+1]=normals[i+1][j+1]+N+E;
   #local j=j+1;
  #end
  #local i=i+1;
 #end
 #local i=0;
 #while(i<imax) #local j=0; #while(j<jmax) #local normals[i][j]=vnormalize(normals[i][j]); #local j=j+1; #end #local i=i+1; #end
 #local i=0;
 #while (i<imax-1) #local j=0;
  #while (j<jmax-1)
   colorquad(
    pt(Mesh[i  ][j  ]),normals[i  ][j  ],hue(Mesh[i  ][j  ].t),
    pt(Mesh[i+1][j  ]),normals[i+1][j  ],hue(Mesh[i+1][j  ].t),
    pt(Mesh[i+1][j+1]),normals[i+1][j+1],hue(Mesh[i+1][j+1].t),
    pt(Mesh[i  ][j+1]),normals[i  ][j+1],hue(Mesh[i  ][j+1].t)
   )
   #local j=j+1;
  #end
  #local i=i+1;
 #end
#end                  

//Scherk-Collins Surface Calculations
#declare n=7; #declare r=pi; #declare R=2*pi;
#macro min2(x1,x2) x1+(x2-x1)/(1-exp(50*(x2-x1))) #end
#macro max2(x1,x2) x1+(x2-x1)/(1-exp(-50*(x2-x1))) #end
#macro Twist(p,theta) <p.x*cos(theta)-p.y*sin(theta),p.x*sin(theta)+p.y*cos(theta)> #end
#macro Warp(p,theta) <(p.x+R)*cos(theta),p.y,(p.x+R)*sin(theta)> #end
#macro IntersRect(p,theta,p1,p2) #local x0=(cos(theta)>0?p2.x:p1.x); #local y0=(sin(theta)>0?p2.y:p1.y);
 #local yint=p.y+(x0-p.x)*tan(theta);
 #if(yint>p1.y & yint<p2.y) #local pint=<x0,yint>; #else #local pint=<p.x+(y0-p.y)*cot(theta),y0>; #end pint
#end
#macro Scherk(Z,i)
 #local X=Re(2*(Ln(Complex(1,0)+Z)-Ln(Complex(1,0)-Z))); #local Y=Re(4*Mult(I,ArcTan(Z)));
 #if(max(abs(X),abs(Y))>r) #local p=IntersRect(<X,Y>,atan3(Y,X),r*<-1,-1>,r*<1,1>); #local X=p.x; #local Y=p.y; #end
 #local Z1=2*pi*i+(1-2*mod(i,2))*Re(2*Mult(I,Ln(Complex(1,0)+Sqr(Z))-Ln(Complex(1,0)-Sqr(Z))));
 Warp(Twist(<X,Y>,sin(2*pi*clock)*Z1/(n+1)),Z1/(n+1))
#end
#macro f(r1,theta) #local p=Scherk(r1*Exp(I*theta),story); <p.x,p.y,p.z,theta/(2*pi)> #end
#declare story=0;
#while(story<=n) ParametricPlot3D(0,1,1/7, 1e-10,2*pi,(2*pi-1e-10)/30) #declare story=story+1; #end
