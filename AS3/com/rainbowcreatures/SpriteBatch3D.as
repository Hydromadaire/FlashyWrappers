/**
 * FLASHYWRAPPERS
 *   
 * @author Pavel Langweil
 * @version 2.55
 *
 * Part of the video replay for Stage3D.
 * 
 */

package com.rainbowcreatures {

    import com.adobe.utils.*;

    import flash.display3D.*;
    import flash.display3D.textures.*;
    import flash.geom.*;
    import flash.system.System;
    import flash.utils.*;

    public class SpriteBatch3D
    {
        private static const SPRITES_PER_BATCH:int = 2048;
        private static const DATA_PER_VERTEX:int = 4;

        private var _context3D:Context3D;

        private var _vertexBuffer:VertexBuffer3D;
        private var _indexBuffer:IndexBuffer3D;
        private var _program:Program3D;
        private var _matrix3D:Matrix3D;
        private var _numSprites:Number;
        private var _sprites:Vector.<SpriteBatch3DSprite>;

        public function SpriteBatch3D(context3D:Context3D)
        { 
            if (context3D == null) {
                throw new ArgumentError("context3D cannot be null.");
            }

            _context3D = context3D;
            allocateBuffers();
            allocateShaders();
            allocateSprites();
        }

        private function allocateSprites():void {
            _sprites = createSprites();
            _numSprites = 0;
        }

        private function createSprites():Vector.<SpriteBatch3DSprite> {
            var sprites:Vector.<SpriteBatch3DSprite> = new Vector.<SpriteBatch3DSprite>(SPRITES_PER_BATCH);

            for (var i:int = 0; i < sprites.length; i++) 
            {
                 sprites[i] = new SpriteBatch3DSprite;
            }

            return sprites;
        }

        private function allocateShaders():void
        {
            var vertexAssembler:AGALMiniAssembler = new AGALMiniAssembler();
            vertexAssembler.assemble(flash.display3D.Context3DProgramType.VERTEX,
                "m44 op, va0, vc0   \n" +
                "mov v0, va1        \n"
            );

            var fragmentAssembler:AGALMiniAssembler = new AGALMiniAssembler();
            fragmentAssembler.assemble(flash.display3D.Context3DProgramType.FRAGMENT, 
                "tex ft1, v0, fs0 <2d,linear> \n" +
                "mov oc, ft1"
            );

            _program = _context3D.createProgram();
            _program.upload(vertexAssembler.agalcode, fragmentAssembler.agalcode);
        }

        private function allocateBuffers():void
        {
            _vertexBuffer = _context3D.createVertexBuffer(SPRITES_PER_BATCH * 4, DATA_PER_VERTEX);
            _vertexBuffer.uploadFromByteArray(createVertexData(), 0, 0, SPRITES_PER_BATCH * 4);

            _indexBuffer = _context3D.createIndexBuffer(SPRITES_PER_BATCH * 6);
            _indexBuffer.uploadFromByteArray(createIndexData(), 0, 0, SPRITES_PER_BATCH * 6);
        }

        private function createVertexData():ByteArray
        {
            var data:ByteArray = new ByteArray;
            data.length = SPRITES_PER_BATCH * 4 * (4 * DATA_PER_VERTEX);
            data.endian = Endian.LITTLE_ENDIAN;

            return data;

        }
        private function createIndexData():ByteArray {

            var data:ByteArray = new ByteArray();
            data.endian = Endian.LITTLE_ENDIAN;
            data.length = (SPRITES_PER_BATCH * 6) * 2;

            for (var i:int = 0; i < SPRITES_PER_BATCH * 6; i++)
            {
                data.writeShort(i * 4);
                data.writeShort(i * 4 + 1);
                data.writeShort(i * 4 + 2);

                data.writeShort(i * 4);
                data.writeShort(i * 4 + 2);
                data.writeShort(i * 4 + 3);
            }

            return data;
        }

        public function dispose():void {
            if (_vertexBuffer) {
                _vertexBuffer.dispose();
                _vertexBuffer = null;
            }

            if (_indexBuffer) {
                _indexBuffer.dispose();
                _indexBuffer = null;
            }
        }

        public function begin(viewWidth:Number, viewHeight:Number):void {
		var near:Number = 0;
		var far:Number = 1;
		var projection:Matrix3D = new Matrix3D(Vector.<Number>
						([
						2/viewWidth, 0, 0, 0,
						0, -2/viewHeight, 0, 0,
						0, 0, 1/(far-near), -near/(far-near),
						0, 0, 0, 1
						]));
            _matrix3D = projection;
        }

        public function draw(texture:VideoTexture, destination:Rectangle,  source:Rectangle=null, origin:Point=null, color:int=0xFFFFFF, rotation:Number=0, depth:Number=0):void {
            if (source == null) {
                source = new Rectangle(0, 0, 1, 1);
            }

            if (origin == null) {
                origin = new Point(0, 0);
            }

            if (_sprites.length == _numSprites) {
                _sprites = _sprites.concat(createSprites());                
            }

            _sprites[_numSprites].texture       = texture;
            _sprites[_numSprites].destination   = destination;
            _sprites[_numSprites].source        = source;
            _sprites[_numSprites].origin        = origin;
            _sprites[_numSprites].rotation      = rotation;
            _sprites[_numSprites].depth         = depth;

            _numSprites++;          
        }

        public function end():void {    
            _context3D.setProgram(_program);
            _context3D.setProgramConstantsFromMatrix(Context3DProgramType.VERTEX, 0, _matrix3D, true);

            _context3D.setVertexBufferAt(0, _vertexBuffer, 0, Context3DVertexBufferFormat.FLOAT_2); // x, y
            _context3D.setVertexBufferAt(1, _vertexBuffer, 2, Context3DVertexBufferFormat.FLOAT_2); // u, v

            // TODO sorting.
            var sprites:Vector.<SpriteBatch3DSprite> = _sprites;
            var texture:VideoTexture = null;
            var firstIndex:int = 0;

            for (var index:int = 0; index < _numSprites; index++) {
                if (texture != sprites[index].texture) {
                    if (index > firstIndex) {
                        renderBatch(texture, sprites, firstIndex, index - firstIndex);
                        firstIndex = index;
                    }

                    texture = sprites[index].texture;
                }
            }

            renderBatch(texture, sprites, firstIndex, _numSprites - firstIndex);
            _numSprites = 0;
        }

        private function renderBatch(texture:VideoTexture, sprites:Vector.<SpriteBatch3DSprite>, offset:int, count:int):void {
            if (_numSprites == 0) {
                return;
            }

            while(count > 0) {
                var data:ByteArray = new ByteArray;
                data.endian = Endian.LITTLE_ENDIAN;

                var size:int = count;
                if (size > SPRITES_PER_BATCH) {
                    size = Math.min(sprites.length, SPRITES_PER_BATCH); 
                }

                for (var i:int = offset; i < (offset + size); i++) {
                    data.writeFloat(sprites[i].destination.left);
                    data.writeFloat(sprites[i].destination.top);
                    data.writeFloat(sprites[i].source.left);
                    data.writeFloat(sprites[i].source.top);

                    data.writeFloat(sprites[i].destination.left);
                    data.writeFloat(sprites[i].destination.bottom);
                    data.writeFloat(sprites[i].source.left);
                    data.writeFloat(sprites[i].source.bottom);

                    data.writeFloat(sprites[i].destination.right);
                    data.writeFloat(sprites[i].destination.bottom);
                    data.writeFloat(sprites[i].source.right);
                    data.writeFloat(sprites[i].source.bottom);

                    data.writeFloat(sprites[i].destination.right);
                    data.writeFloat(sprites[i].destination.top);
                    data.writeFloat(sprites[i].source.right);
                    data.writeFloat(sprites[i].source.top);
                }

                _vertexBuffer.uploadFromByteArray(data, 0, 0, size * 4);            
                _context3D.setTextureAt(0, texture);
                _context3D.drawTriangles(_indexBuffer, 0, size * 2);
                offset += size;
                count -= size;
            }
        }
    }
}

import flash.display3D.textures.*;
import flash.geom.*;

internal class SpriteBatch3DSprite {
    public var texture:VideoTexture;
    public var source:Rectangle;
    public var destination:Rectangle;
    public var origin:Point;
    public var rotation:Number;
    public var depth:Number;
}
